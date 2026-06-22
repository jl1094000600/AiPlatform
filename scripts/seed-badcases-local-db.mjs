import net from 'node:net';
import crypto from 'node:crypto';
import fs from 'node:fs';

const env = readEnv('.env');
const jdbcUrl = env.DB_URL || 'jdbc:mysql://localhost:3306/ai_platform';
const { host, port, database } = parseJdbcUrl(jdbcUrl);
const user = env.DB_USERNAME || 'root';
const password = env.DB_PASSWORD || '';

const PRD_TYPES = [
  ['REQUIREMENT_HALLUCINATION', 'The PRD invents business actors, integrations, or workflow steps that were not provided.'],
  ['MISSING_BUSINESS_GOAL', 'The PRD lists features but does not define the target outcome or measurable success metric.'],
  ['ROLE_CONFUSION', 'The PRD mixes operator, admin, reviewer, and end-user responsibilities.'],
  ['CONFLICTING_REQUIREMENTS', 'The PRD accepts mutually conflicting constraints without calling out trade-offs.'],
  ['MISSING_ACCEPTANCE_CRITERIA', 'The PRD cannot be reviewed because acceptance criteria are absent or vague.'],
  ['MVP_SCOPE_CREEP', 'The PRD expands a small request into a large platform without an MVP boundary.'],
  ['SECURITY_OMISSION', 'The PRD omits sensitive data protection, permission, audit, or compliance requirements.'],
  ['ERROR_FLOW_MISSING', 'The PRD only covers the happy path and ignores failure, retry, rollback, or recovery.'],
  ['DATA_CONTRACT_GAP', 'The PRD does not specify required fields, status values, validation rules, or ownership.'],
  ['QUALITY_LOOP_MISSING', 'The PRD does not describe feedback capture, badcase labeling, or version comparison.']
];

const CODE_TYPES = [
  ['NON_RUNNABLE_CODE', 'Generated code misses imports, dependencies, configuration, or entry wiring.'],
  ['TECH_STACK_MISMATCH', 'Generated code uses a framework or style that does not match the existing project.'],
  ['API_HALLUCINATION', 'Generated code calls endpoints, fields, or SDK methods that do not exist.'],
  ['DATA_SCHEMA_MISMATCH', 'Generated code uses names, types, or required flags that differ from the PRD.'],
  ['ERROR_HANDLING_MISSING', 'Generated code handles only the happy path and drops timeout, empty, or failed states.'],
  ['SECURITY_RISK', 'Generated code exposes secrets, skips auth checks, or accepts unsafe user input.'],
  ['PERMISSION_BYPASS', 'Generated code hides UI actions but does not enforce backend permission checks.'],
  ['PERFORMANCE_RISK', 'Generated code loads large datasets synchronously or performs repeated expensive queries.'],
  ['UNSCOPED_CHANGE', 'Generated code changes unrelated files, global styles, or shared contracts.'],
  ['TEST_GAP', 'Generated code changes behavior without focused unit, API, or UI verification.']
];

const E2E_TYPES = [
  ['PRD_CODE_FIELD_DRIFT', 'The code introduces fields, states, or modules not present in the approved PRD.'],
  ['PRD_EXCEPTION_NOT_IMPLEMENTED', 'The PRD defines an exception flow but the generated code omits it.'],
  ['PRD_PERMISSION_NOT_IMPLEMENTED', 'The PRD defines role permissions but the generated code enforces them incompletely.'],
  ['PRD_METRIC_NOT_INSTRUMENTED', 'The PRD defines measurable quality metrics but the code has no tracking point.'],
  ['PRD_NAMING_INCONSISTENCY', 'Business nouns in the PRD are renamed inconsistently in routes, models, or UI labels.']
];

const PRD_SCENARIOS = ['AI customer support console', 'code generation workspace', 'enterprise approval center', 'dataset evaluation dashboard'];
const CODE_SCENARIOS = ['login and permission module', 'file upload endpoint', 'generated code preview panel', 'tenant billing report'];
const E2E_SCENARIOS = ['PRD-to-code delivery pipeline', 'manual review workflow', 'generated artifact archive', 'badcase analysis loop'];

class MysqlClient {
  constructor({ host, port, user, password, database }) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.database = database;
    this.socket = null;
    this.buffer = Buffer.alloc(0);
    this.sequence = 0;
  }

  async connect() {
    this.socket = net.createConnection({ host: this.host, port: this.port });
    this.socket.on('data', chunk => {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      if (this.waiting) this.waiting();
    });
    await once(this.socket, 'connect');
    const handshake = await this.readPacket();
    const info = parseHandshake(handshake);
    await this.authenticate(info);
  }

  async authenticate(info) {
    this.sequence = 1;
    const flags =
      0x00000001 | // CLIENT_LONG_PASSWORD
      0x00000004 | // CLIENT_LONG_FLAG
      0x00000200 | // CLIENT_PROTOCOL_41
      0x00002000 | // CLIENT_TRANSACTIONS
      0x00008000 | // CLIENT_SECURE_CONNECTION
      0x00080000 | // CLIENT_PLUGIN_AUTH
      0x00000008;  // CLIENT_CONNECT_WITH_DB
    const token = authToken(info.plugin, this.password, info.scramble);
    const payload = Buffer.concat([
      uint32(flags),
      uint32(0x01000000),
      Buffer.from([45]),
      Buffer.alloc(23),
      nul(this.user),
      Buffer.from([token.length]),
      token,
      nul(this.database),
      nul(info.plugin)
    ]);
    await this.writePacket(payload);
    await this.finishAuth();
  }

  async finishAuth() {
    let packet = await this.readPacket();
    if (packet[0] === 0xfe) {
      const zero = packet.indexOf(0, 1);
      const plugin = packet.subarray(1, zero).toString();
      const scramble = packet.subarray(zero + 1);
      this.sequence = 3;
      await this.writePacket(authToken(plugin, this.password, scramble));
      packet = await this.readPacket();
    }
    if (packet[0] === 0x01 && packet[1] === 0x03) {
      packet = await this.readPacket();
    }
    if (packet[0] === 0xff) throw mysqlError(packet);
    if (packet[0] !== 0x00) throw new Error(`Unexpected auth packet: ${packet.toString('hex')}`);
  }

  async query(sql) {
    this.sequence = 0;
    await this.writePacket(Buffer.concat([Buffer.from([0x03]), Buffer.from(sql)]));
    const first = await this.readPacket();
    if (first[0] === 0xff) throw mysqlError(first, sql);
    if (first[0] === 0x00) return { ok: true };
    const columnCount = readLenencInt(first, 0).value;
    const columns = [];
    for (let i = 0; i < columnCount; i += 1) {
      const columnPacket = await this.readPacket();
      columns.push(parseColumnName(columnPacket));
    }
    await this.readEof();
    const rows = [];
    while (true) {
      const rowPacket = await this.readPacket();
      if (isEof(rowPacket)) break;
      rows.push(parseRow(rowPacket, columns));
    }
    return rows;
  }

  async readEof() {
    const packet = await this.readPacket();
    if (!isEof(packet)) throw new Error(`Expected EOF packet, got ${packet.toString('hex')}`);
  }

  async readPacket() {
    while (this.buffer.length < 4) await this.waitForData();
    const length = this.buffer[0] | (this.buffer[1] << 8) | (this.buffer[2] << 16);
    while (this.buffer.length < length + 4) await this.waitForData();
    const payload = this.buffer.subarray(4, 4 + length);
    this.buffer = this.buffer.subarray(4 + length);
    return payload;
  }

  async writePacket(payload) {
    const header = Buffer.alloc(4);
    header[0] = payload.length & 0xff;
    header[1] = (payload.length >> 8) & 0xff;
    header[2] = (payload.length >> 16) & 0xff;
    header[3] = this.sequence++ & 0xff;
    this.socket.write(Buffer.concat([header, payload]));
  }

  waitForData() {
    return new Promise(resolve => {
      this.waiting = () => {
        this.waiting = null;
        resolve();
      };
    });
  }

  close() {
    this.socket?.end();
  }
}

async function main() {
  const client = new MysqlClient({ host, port, user, password, database });
  await client.connect();
  try {
    await client.query(createBadcaseTableSql());
    await ensureTenantColumn(client);
    await client.query(menuSql());
    await client.query(updateMenuParentSql());
    for (const row of buildSeedRows()) {
      await client.query(insertSeedSql(row));
    }
    const badcaseCount = await client.query("SELECT COUNT(*) AS total FROM bad_case_record WHERE case_code LIKE 'BC-COLD-%'");
    const tenantBadcaseCount = await client.query("SELECT COUNT(*) AS total FROM bad_case_record WHERE tenant_id = 1 AND case_code LIKE 'BC-COLD-%'");
    const menuCount = await client.query("SELECT COUNT(*) AS total FROM sys_menu WHERE menu_code = 'badcases' AND path = '/badcases'");
    const sampleRows = await client.query("SELECT case_code, stage, badcase_type FROM bad_case_record WHERE case_code LIKE 'BC-COLD-%' ORDER BY case_code LIMIT 3");
    console.log(JSON.stringify({
      database,
      coldStartBadcases: Number(firstValue(badcaseCount[0])),
      tenantOneColdStartBadcases: Number(firstValue(tenantBadcaseCount[0])),
      badcaseMenuRows: Number(firstValue(menuCount[0])),
      samples: sampleRows
    }, null, 2));
  } finally {
    client.close();
  }
}

async function ensureTenantColumn(client) {
  const rows = await client.query("SELECT COUNT(*) AS total FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bad_case_record' AND COLUMN_NAME = 'tenant_id'");
  if (Number(firstValue(rows[0])) === 0) {
    await client.query("ALTER TABLE bad_case_record ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id");
    await client.query("ALTER TABLE bad_case_record ADD KEY idx_bad_case_tenant_stage (tenant_id, stage, create_time)");
  }
  await client.query("UPDATE bad_case_record SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0");
}

function buildSeedRows() {
  const rows = [];
  let index = 1;
  for (const type of PRD_TYPES) for (const scenario of PRD_SCENARIOS) rows.push(seedRow(index++, 'PRD', type, scenario));
  for (const type of CODE_TYPES) for (const scenario of CODE_SCENARIOS) rows.push(seedRow(index++, 'CODE', type, scenario));
  for (const type of E2E_TYPES) for (const scenario of E2E_SCENARIOS) rows.push(seedRow(index++, 'PRD_TO_CODE', type, scenario));
  return rows;
}

function seedRow(index, stage, type, scenario) {
  return {
    caseCode: `BC-COLD-${String(index).padStart(3, '0')}`,
    stage,
    badcaseType: type[0],
    severity: index % 17 === 0 ? 'P0' : (index % 3 === 0 ? 'P2' : 'P1'),
    projectName: 'Cold Start Badcase Library',
    requirementTitle: `${scenario} - ${type[0].toLowerCase().replaceAll('_', ' ')}`,
    inputPrompt: `Build ${scenario} and generate ${stage === 'CODE' ? 'implementation code' : 'a PRD'}.`,
    generatedPrd: stage === 'CODE' ? null : `Generated PRD excerpt: broad feature list for ${scenario} with insufficient constraints around ${type[0]}.`,
    generatedCode: stage === 'PRD' ? null : `Generated code excerpt: placeholder implementation for ${scenario} that demonstrates ${type[0]}.`,
    expectedBehavior: expectedFor(stage),
    failureReason: `${type[1]} Scenario: ${scenario}.`,
    tags: `cold-start,${stage.toLowerCase()},${type[0].toLowerCase()}`
  };
}

function expectedFor(stage) {
  if (stage === 'PRD') return 'The PRD should state assumptions, ask for missing context when needed, define scope, roles, data, exceptions, and acceptance criteria.';
  if (stage === 'CODE') return 'The generated code should follow the existing stack, compile, enforce security and permissions, and include focused verification.';
  return 'The generated code should faithfully implement the reviewed PRD without drifting in naming, fields, permissions, exceptions, or metrics.';
}

function createBadcaseTableSql() {
  return `CREATE TABLE IF NOT EXISTS bad_case_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    case_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    badcase_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    project_name VARCHAR(128) DEFAULT NULL,
    requirement_title VARCHAR(256) DEFAULT NULL,
    input_prompt MEDIUMTEXT DEFAULT NULL,
    generated_prd MEDIUMTEXT DEFAULT NULL,
    generated_code MEDIUMTEXT DEFAULT NULL,
    expected_behavior MEDIUMTEXT DEFAULT NULL,
    failure_reason MEDIUMTEXT DEFAULT NULL,
    reviewed_by VARCHAR(64) DEFAULT NULL,
    pipeline_id BIGINT DEFAULT NULL,
    stage_run_id BIGINT DEFAULT NULL,
    batch_id BIGINT DEFAULT NULL,
    feedback_id BIGINT DEFAULT NULL,
    approval_id BIGINT DEFAULT NULL,
    tags VARCHAR(512) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bad_case_code (case_code),
    KEY idx_bad_case_tenant_stage (tenant_id, stage, create_time),
    KEY idx_bad_case_stage (stage, create_time),
    KEY idx_bad_case_type (badcase_type),
    KEY idx_bad_case_source (source_type),
    KEY idx_bad_case_pipeline (pipeline_id, stage_run_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`;
}

function menuSql() {
  return `INSERT INTO sys_menu (menu_code, menu_name, path, icon, permission_code, sort_order, visible, status, is_deleted)
    VALUES ('badcases', 'Badcase Analysis', '/badcases', 'DataAnalysis', 'automation:list', 15, 1, 1, 0)
    ON DUPLICATE KEY UPDATE
      menu_name = VALUES(menu_name),
      path = VALUES(path),
      icon = VALUES(icon),
      permission_code = VALUES(permission_code),
      sort_order = VALUES(sort_order),
      visible = 1,
      status = 1,
      is_deleted = 0`;
}

function updateMenuParentSql() {
  return `UPDATE sys_menu child
    JOIN sys_menu parent ON parent.menu_code = 'group-automation'
    SET child.parent_id = parent.id
    WHERE child.menu_code = 'badcases'`;
}

function insertSeedSql(row) {
  const columns = [
    'case_code', 'source_type', 'stage', 'badcase_type', 'severity', 'project_name', 'requirement_title',
    'input_prompt', 'generated_prd', 'generated_code', 'expected_behavior', 'failure_reason', 'reviewed_by', 'tags'
  ];
  const values = [
    row.caseCode, 'SEED', row.stage, row.badcaseType, row.severity, row.projectName, row.requirementTitle,
    row.inputPrompt, row.generatedPrd, row.generatedCode, row.expectedBehavior, row.failureReason, 'cold-start', row.tags
  ];
  return `INSERT INTO bad_case_record (${columns.join(', ')}) VALUES (${values.map(sqlValue).join(', ')})
    ON DUPLICATE KEY UPDATE
      stage = VALUES(stage),
      badcase_type = VALUES(badcase_type),
      severity = VALUES(severity),
      project_name = VALUES(project_name),
      requirement_title = VALUES(requirement_title),
      input_prompt = VALUES(input_prompt),
      generated_prd = VALUES(generated_prd),
      generated_code = VALUES(generated_code),
      expected_behavior = VALUES(expected_behavior),
      failure_reason = VALUES(failure_reason),
      reviewed_by = VALUES(reviewed_by),
      tags = VALUES(tags)`;
}

function sqlValue(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`;
}

function readEnv(path) {
  const result = {};
  if (!fs.existsSync(path)) return result;
  for (const line of fs.readFileSync(path, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const index = trimmed.indexOf('=');
    if (index < 0) continue;
    result[trimmed.slice(0, index)] = trimmed.slice(index + 1);
  }
  return result;
}

function parseJdbcUrl(value) {
  const match = value.match(/^jdbc:mysql:\/\/([^/:?]+)(?::(\d+))?\/([^?]+)/);
  if (!match) throw new Error(`Unsupported DB_URL: ${value}`);
  return { host: match[1], port: Number(match[2] || 3306), database: match[3] };
}

function firstValue(row) {
  if (!row) return null;
  if (row.total !== undefined) return row.total;
  const values = Object.values(row);
  return values.length ? values[0] : null;
}

function parseHandshake(packet) {
  let offset = 0;
  offset += 1;
  while (packet[offset] !== 0) offset += 1;
  offset += 1;
  offset += 4;
  const part1 = packet.subarray(offset, offset + 8);
  offset += 9;
  const capabilityLow = packet.readUInt16LE(offset);
  offset += 2;
  offset += 1;
  offset += 2;
  const capabilityHigh = packet.readUInt16LE(offset);
  offset += 2;
  const capabilities = capabilityLow | (capabilityHigh << 16);
  const authLength = packet[offset] || 21;
  offset += 1;
  offset += 10;
  const part2Length = Math.max(13, authLength - 8);
  const part2 = packet.subarray(offset, offset + part2Length).filter(byte => byte !== 0);
  offset += part2Length;
  const plugin = (packet.subarray(offset).toString().split('\0')[0] || 'mysql_native_password');
  return { capabilities, scramble: Buffer.concat([part1, part2]), plugin };
}

function authToken(plugin, pass, scramble) {
  if (!pass) return Buffer.alloc(0);
  if (plugin === 'mysql_native_password') {
    const s1 = sha1(pass);
    const s2 = sha1(s1);
    const s3 = sha1(Buffer.concat([scramble, s2]));
    return xor(s1, s3);
  }
  const p1 = sha256(pass);
  const p2 = sha256(p1);
  const p3 = sha256(Buffer.concat([p2, scramble]));
  return xor(p1, p3);
}

function parseColumnName(packet) {
  let offset = 0;
  const parts = [];
  for (let i = 0; i < 6; i += 1) {
    const len = readLenencInt(packet, offset);
    offset = len.next;
    parts.push(packet.subarray(offset, offset + len.value).toString());
    offset += len.value;
  }
  return parts[5];
}

function parseRow(packet, columns) {
  let offset = 0;
  const row = {};
  for (const column of columns) {
    if (packet[offset] === 0xfb) {
      row[column] = null;
      offset += 1;
      continue;
    }
    const len = readLenencInt(packet, offset);
    offset = len.next;
    row[column] = packet.subarray(offset, offset + len.value).toString();
    offset += len.value;
  }
  return row;
}

function readLenencInt(packet, offset) {
  const first = packet[offset];
  if (first < 0xfb) return { value: first, next: offset + 1 };
  if (first === 0xfc) return { value: packet.readUInt16LE(offset + 1), next: offset + 3 };
  if (first === 0xfd) return { value: packet[offset + 1] | (packet[offset + 2] << 8) | (packet[offset + 3] << 16), next: offset + 4 };
  return { value: Number(packet.readBigUInt64LE(offset + 1)), next: offset + 9 };
}

function mysqlError(packet, sql = '') {
  const code = packet.readUInt16LE(1);
  const message = packet.subarray(9).toString() || packet.subarray(3).toString();
  return new Error(`MySQL ${code}: ${message}${sql ? `\nSQL: ${sql}` : ''}`);
}

function isEof(packet) {
  return packet[0] === 0xfe && packet.length < 9;
}

function sha1(value) {
  return crypto.createHash('sha1').update(value).digest();
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest();
}

function xor(a, b) {
  const out = Buffer.alloc(a.length);
  for (let i = 0; i < a.length; i += 1) out[i] = a[i] ^ b[i];
  return out;
}

function uint32(value) {
  const buffer = Buffer.alloc(4);
  buffer.writeUInt32LE(value);
  return buffer;
}

function nul(value) {
  return Buffer.concat([Buffer.from(value), Buffer.from([0])]);
}

function once(emitter, event) {
  return new Promise((resolve, reject) => {
    emitter.once(event, resolve);
    emitter.once('error', reject);
  });
}

main().catch(error => {
  console.error(error.message);
  process.exit(1);
});
