import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const composeFile = resolve(repoRoot, 'deploy/docker-compose.yml')
const envFile = resolve(repoRoot, 'deploy/.env.example')
const siteConfig = resolve(repoRoot, 'deploy/nginx/aiplatform.conf')
const sqlInit = resolve(repoRoot, 'backend/sql/init.sql')
const bootstrapSql = resolve(repoRoot, 'deploy/mysql/00-bootstrap.sql')
const ciFile = resolve(repoRoot, '.github/workflows/ci.yml')

function fail(message) {
  console.error(`compose check failed: ${message}`)
  process.exit(1)
}

for (const requiredFile of [composeFile, envFile, siteConfig, sqlInit, bootstrapSql, ciFile]) {
  if (!existsSync(requiredFile)) fail(`missing file: ${requiredFile}`)
}

const compose = readFileSync(composeFile, 'utf8')
const bootstrap = readFileSync(bootstrapSql, 'utf8')
const nginx = readFileSync(siteConfig, 'utf8')
const ci = readFileSync(ciFile, 'utf8')
const servicesBlock = compose.match(/^services:\s*\r?\n([\s\S]*?)^networks:/m)?.[1]
if (!servicesBlock) fail('cannot locate services block')

const actualServices = [...servicesBlock.matchAll(/^  ([a-zA-Z0-9_-]+):\s*$/gm)].map(match => match[1]).sort()
const expectedServices = ['backend', 'front', 'mysql', 'redis']
if (JSON.stringify(actualServices) !== JSON.stringify(expectedServices)) {
  fail(`services are ${actualServices.join(', ')}, expected ${expectedServices.join(', ')}`)
}

if (!nginx.includes('server backend:8080;')) fail('Nginx must proxy to backend:8080')
if (!compose.includes('http://localhost:8080/api/actuator/health')) fail('backend health path is inconsistent')
if (!compose.includes('./mysql/00-bootstrap.sql:/docker-entrypoint-initdb.d/00-bootstrap.sql:ro')) fail('MySQL bootstrap mount is inconsistent')
if (!compose.includes('../backend/sql:/opt/aiplatform/sql:ro')) fail('MySQL migration directory mount is missing')
if (!compose.includes('../backend/src/main/resources:/opt/aiplatform/resources:ro')) fail('MySQL resource schema mount is missing')
for (const match of bootstrap.matchAll(/^SOURCE\s+([^;]+);/gm)) {
  const source = match[1].trim()
  const localPath = source.startsWith('/opt/aiplatform/sql/')
    ? resolve(repoRoot, 'backend/sql', source.slice('/opt/aiplatform/sql/'.length))
    : source.startsWith('/opt/aiplatform/resources/')
      ? resolve(repoRoot, 'backend/src/main/resources', source.slice('/opt/aiplatform/resources/'.length))
      : null
  if (!localPath || !existsSync(localPath)) fail(`bootstrap references missing schema: ${source}`)
}
for (const requiredEnvironment of ['DB_URL:', 'DB_USERNAME:', 'DB_PASSWORD:', 'BOOTSTRAP_ADMIN_PASSWORD_HASH:']) {
  if (!servicesBlock.includes(requiredEnvironment)) fail(`backend environment is missing ${requiredEnvironment}`)
}
if ((servicesBlock.match(/^    healthcheck:/gm) ?? []).length !== expectedServices.length) fail('every service must define a healthcheck')
if (!ci.includes('run: npm run test:business')) fail('CI must run test:business without suppressing failures')
if (/test:business[^\r\n]*(?:\|\|\s*true|continue-on-error)/.test(ci)) fail('CI suppresses test:business failures')

const dockerVersion = spawnSync('docker', ['compose', 'version'], { encoding: 'utf8', shell: process.platform === 'win32' })
if (dockerVersion.status !== 0) {
  console.log('compose check: textual topology and path checks passed; Docker is unavailable, rendered config skipped')
  process.exit(0)
}

const rendered = spawnSync(
  'docker',
  ['compose', '--env-file', envFile, '-f', composeFile, 'config', '--format', 'json'],
  { encoding: 'utf8', shell: process.platform === 'win32' }
)
if (rendered.status !== 0) fail(rendered.stderr.trim() || 'docker compose config failed')

const config = JSON.parse(rendered.stdout)
const renderedServices = config.services ?? {}
if (JSON.stringify(Object.keys(renderedServices).sort()) !== JSON.stringify(expectedServices)) {
  fail('rendered service topology is inconsistent')
}

for (const [name, service] of Object.entries(renderedServices)) {
  if (!service.healthcheck) fail(`${name} has no rendered healthcheck`)
  for (const volume of service.volumes ?? []) {
    if (volume.type === 'bind' && !existsSync(volume.source)) fail(`missing bind source for ${name}: ${volume.source}`)
  }
}

const backendTargets = new Set((renderedServices.backend.ports ?? []).map(port => port.target))
if (backendTargets.size !== 1 || !backendTargets.has(8080)) fail('backend container port must be 8080')
if (renderedServices.front.depends_on?.backend?.condition !== 'service_healthy') fail('front must wait for a healthy backend')

console.log('compose check: rendered topology and bind mounts passed')
