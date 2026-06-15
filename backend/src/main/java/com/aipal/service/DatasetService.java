package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import com.aipal.entity.AiDataset;
import com.aipal.mapper.AiDatasetMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("json", "jsonl", "csv", "xml", "xls", "xlsx", "txt");
    private static final long MAX_CSV_IMPORT_BYTES = 100L * 1024 * 1024;
    private static final long MAX_OTHER_IMPORT_BYTES = 50L * 1024 * 1024;

    private final AiDatasetMapper datasetMapper;
    private final DataGeneratorService dataGeneratorService;
    private final ObjectMapper objectMapper;

    @Value("${aipal.dataset.storage-dir:${java.io.tmpdir}/datasets}")
    private String storageDirectory;

    public Page<AiDataset> listDatasets(int pageNum, int pageSize, String name, String category, String format) {
        Page<AiDataset> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200));
        QueryWrapper<AiDataset> wrapper = new QueryWrapper<>();
        if (name != null && !name.isBlank()) wrapper.like("dataset_name", name.trim());
        if (category != null && !category.isBlank()) wrapper.eq("category", category.trim());
        if (format != null && !format.isBlank()) wrapper.eq("format", format.toLowerCase(Locale.ROOT));
        wrapper.eq("status", 1).orderByDesc("create_time");
        return datasetMapper.selectPage(page, wrapper);
    }

    public AiDataset getDatasetById(Long id) {
        AiDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) throw new IllegalArgumentException("Dataset not found: " + id);
        return dataset;
    }

    public AiDataset importDataset(DatasetImportRequest request, MultipartFile file) throws IOException {
        validateImportRequest(request, file);
        String format = normalizeFormat(request.getFormat());
        long maxBytes = maxImportBytes(format);
        byte[] content = file != null && !file.isEmpty()
                ? readUpload(file, maxBytes) : downloadSource(request.getSourceUrl(), maxBytes);
        Path storedPath = saveBytes(content, format);

        try {
            List<Map<String, Object>> records = parseRecords(storedPath, format);
            AiDataset dataset = newDataset(request, format);
            dataset.setFilePath(storedPath.toString());
            dataset.setSize((long) content.length);
            dataset.setRecordCount(records.size());
            dataset.setFieldSchema(toFieldSchema(request.getFields(), records));
            datasetMapper.insert(dataset);
            return dataset;
        } catch (Exception e) {
            Files.deleteIfExists(storedPath);
            if (e instanceof IOException ioException) throw ioException;
            throw e;
        }
    }

    public AiDataset generateDataset(DatasetImportRequest request) {
        validateDatasetName(request);
        String format = normalizeFormat(request.getFormat() == null ? "json" : request.getFormat());
        if (!List.of("json", "csv").contains(format)) {
            throw new IllegalArgumentException("Generated datasets support only json or csv format");
        }
        int count = request.getCount() == null ? DataGeneratorService.MIN_GENERATION_COUNT : request.getCount();
        List<String[]> generatedData = dataGeneratorService.generateData(request.getFields(), count);

        AiDataset dataset = newDataset(request, format);
        dataset.setFieldSchema(toFieldSchema(request.getFields(), List.of()));
        dataset.setRecordCount(generatedData.size());
        String filePath = dataGeneratorService.saveGeneratedData(
                dataset.getDatasetCode(), request.getFields(), generatedData, format, storageRoot());
        dataset.setFilePath(filePath);
        try {
            dataset.setSize(Files.size(Path.of(filePath)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect generated dataset", e);
        }
        datasetMapper.insert(dataset);
        return dataset;
    }

    public List<Map<String, Object>> previewDataset(Long id, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<Map<String, Object>> records = loadDatasetRecords(getDatasetById(id));
        return records.subList(0, Math.min(safeLimit, records.size()));
    }

    public List<Map<String, Object>> loadDatasetRecords(AiDataset dataset) {
        if (dataset == null || dataset.getFilePath() == null || dataset.getFilePath().isBlank()) {
            throw new IllegalArgumentException("Dataset file is not available");
        }
        Path path = Path.of(dataset.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot())) throw new IllegalArgumentException("Dataset file is outside managed storage");
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Dataset file not found");
        try {
            return parseRecords(path, normalizeFormat(dataset.getFormat()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read dataset", e);
        }
    }

    public boolean updateDataset(AiDataset incoming) {
        if (incoming == null || incoming.getId() == null) throw new IllegalArgumentException("Dataset id is required");
        AiDataset existing = getDatasetById(incoming.getId());
        if (incoming.getDatasetName() != null && !incoming.getDatasetName().isBlank()) {
            existing.setDatasetName(incoming.getDatasetName().trim());
        }
        if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
        if (incoming.getCategory() != null) existing.setCategory(incoming.getCategory());
        if (incoming.getStatus() != null) existing.setStatus(incoming.getStatus());
        existing.setUpdateTime(LocalDateTime.now());
        return datasetMapper.updateById(existing) > 0;
    }

    public boolean deleteDataset(Long id) {
        AiDataset dataset = getDatasetById(id);
        boolean deleted = datasetMapper.deleteById(id) > 0;
        if (deleted) deleteManagedFile(dataset.getFilePath());
        return deleted;
    }

    public int deleteDatasets(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Dataset ids are required");
        int deleted = 0;
        for (Long id : ids.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            if (deleteDataset(id)) deleted++;
        }
        return deleted;
    }

    public List<String> getSupportedFormats() {
        return SUPPORTED_FORMATS.stream().sorted().toList();
    }

    private AiDataset newDataset(DatasetImportRequest request, String format) {
        Long ownerId = TenantContext.userId();
        if (ownerId == null) throw new IllegalStateException("Authenticated user is required");
        AiDataset dataset = new AiDataset();
        dataset.setDatasetCode("DS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        dataset.setDatasetName(request.getDatasetName().trim());
        dataset.setDescription(request.getDescription());
        dataset.setCategory(request.getCategory());
        dataset.setFormat(format);
        dataset.setOwnerId(ownerId);
        dataset.setStatus(1);
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());
        return dataset;
    }

    private void validateImportRequest(DatasetImportRequest request, MultipartFile file) {
        validateDatasetName(request);
        normalizeFormat(request.getFormat());
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasUrl = request.getSourceUrl() != null && !request.getSourceUrl().isBlank();
        if (hasFile == hasUrl) throw new IllegalArgumentException("Provide exactly one file or sourceUrl");
        if (hasFile && file.getSize() > maxImportBytes(normalizeFormat(request.getFormat()))) {
            throw new IllegalArgumentException("Dataset exceeds format size limit");
        }
    }

    private void validateDatasetName(DatasetImportRequest request) {
        if (request == null || request.getDatasetName() == null || request.getDatasetName().isBlank()) {
            throw new IllegalArgumentException("Dataset name is required");
        }
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported format: " + format + ". Supported: " + SUPPORTED_FORMATS);
        }
        return normalized;
    }

    private long maxImportBytes(String format) {
        return "csv".equals(format) ? MAX_CSV_IMPORT_BYTES : MAX_OTHER_IMPORT_BYTES;
    }

    private byte[] readUpload(MultipartFile file, long maxBytes) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return readLimited(input, maxBytes);
        }
    }

    private byte[] downloadSource(String sourceUrl, long maxBytes) throws IOException {
        URI uri;
        try {
            uri = URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sourceUrl", e);
        }
        validatePublicHttpUri(uri);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Source URL returned HTTP " + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maxBytes) throw new IllegalArgumentException("Remote dataset exceeds format size limit");
            try (InputStream body = response.body()) {
                return readLimited(body, maxBytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("URL import interrupted", e);
        }
    }

    void validatePublicHttpUri(URI uri) throws IOException {
        if (uri == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("Invalid source URL host");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Source URL must resolve to a public address");
            }
        }
    }

    private byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("Dataset exceeds format size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Path saveBytes(byte[] content, String format) throws IOException {
        Path base = storageRoot();
        Files.createDirectories(base);
        Path target = base.resolve(UUID.randomUUID() + "." + format).normalize();
        Files.write(target, content);
        return target;
    }

    private Path storageRoot() {
        return Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    private void deleteManagedFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot())) {
            log.warn("Refusing to delete unmanaged dataset file: {}", path);
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Dataset row deleted but file cleanup failed: {}", path, e);
        }
    }

    private List<Map<String, Object>> parseRecords(Path path, String format) throws IOException {
        return switch (format) {
            case "json" -> parseJson(readText(path));
            case "jsonl" -> parseJsonLines(readText(path).lines().toList());
            case "csv" -> parseCsv(readText(path));
            case "txt" -> parseText(readText(path).lines().toList());
            case "xml" -> parseXml(Files.readAllBytes(path));
            case "xls", "xlsx" -> parseExcel(path);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    private String readText(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return Charset.forName("GBK").decode(ByteBuffer.wrap(content)).toString();
        }
    }

    private List<Map<String, Object>> parseExcel(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.rowIterator();
            if (!rows.hasNext()) return List.of();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row headerRow = rows.next();
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(column)));
            }
            List<Map<String, Object>> records = new ArrayList<>();
            while (rows.hasNext()) {
                Row row = rows.next();
                Map<String, Object> record = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    record.put(headers.get(column), formatter.formatCellValue(row.getCell(column)));
                }
                records.add(record);
            }
            return records;
        } catch (Exception exception) {
            throw new IOException("Invalid Excel dataset", exception);
        }
    }

    private List<Map<String, Object>> parseJson(String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        if (root == null || root.isNull()) return List.of();
        JsonNode records = root;
        if (root.isObject()) {
            for (String key : List.of("records", "data", "items")) {
                if (root.has(key) && root.get(key).isArray()) {
                    records = root.get(key);
                    break;
                }
            }
        }
        if (records.isArray()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : records) result.add(asRecord(node));
            return result;
        }
        return List.of(asRecord(records));
    }

    private Map<String, Object> asRecord(JsonNode node) {
        if (node.isObject()) return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
        return new LinkedHashMap<>(Map.of("value", objectMapper.convertValue(node, Object.class)));
    }

    private List<Map<String, Object>> parseJsonLines(List<String> lines) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : lines) if (!line.isBlank()) records.add(asRecord(objectMapper.readTree(line)));
        return records;
    }

    private List<Map<String, Object>> parseCsv(String content) {
        List<List<String>> rows = readCsvRows(content);
        if (rows.isEmpty()) return List.of();
        List<String> headers = rows.get(0);
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).stream().allMatch(String::isBlank)) continue;
            Map<String, Object> record = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                record.put(headers.get(j), j < rows.get(i).size() ? rows.get(i).get(j) : "");
            }
            records.add(record);
        }
        return records;
    }

    private List<List<String>> readCsvRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                row.add(value.toString());
                value.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                row.add(value.toString());
                rows.add(row);
                row = new ArrayList<>();
                value.setLength(0);
            } else value.append(ch);
        }
        if (!row.isEmpty() || value.length() > 0) {
            row.add(value.toString());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> parseText(List<String> lines) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : lines) if (!line.isBlank()) records.add(new LinkedHashMap<>(Map.of("text", line)));
        return records;
    }

    private List<Map<String, Object>> parseXml(byte[] content) throws IOException {
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            Element root = document.getDocumentElement();
            List<Element> elements = childElements(root);
            if (elements.isEmpty()) return List.of(Map.of(root.getTagName(), root.getTextContent().trim()));
            List<Map<String, Object>> records = new ArrayList<>();
            for (Element element : elements) {
                Map<String, Object> record = new LinkedHashMap<>();
                List<Element> fields = childElements(element);
                if (fields.isEmpty()) record.put(element.getTagName(), element.getTextContent().trim());
                else for (Element field : fields) record.put(field.getTagName(), field.getTextContent().trim());
                records.add(record);
            }
            return records;
        } catch (Exception e) {
            throw new IOException("Invalid XML dataset", e);
        }
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element element) elements.add(element);
        return elements;
    }

    private List<Map<String, Object>> parseXlsx(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            List<String> sharedStrings = readSharedStrings(zip);
            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) throw new IOException("XLSX has no first worksheet");
            Document sheet = secureDocumentBuilderFactory().newDocumentBuilder().parse(zip.getInputStream(sheetEntry));
            NodeList rowNodes = sheet.getElementsByTagName("row");
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < rowNodes.getLength(); i++) {
                Element row = (Element) rowNodes.item(i);
                NodeList cells = row.getElementsByTagName("c");
                List<String> values = new ArrayList<>();
                for (int j = 0; j < cells.getLength(); j++) {
                    Element cell = (Element) cells.item(j);
                    String reference = cell.getAttribute("r");
                    int column = columnIndex(reference);
                    while (values.size() <= column) values.add("");
                    NodeList valueNodes = cell.getElementsByTagName("v");
                    String value = valueNodes.getLength() == 0 ? "" : valueNodes.item(0).getTextContent();
                    if ("s".equals(cell.getAttribute("t")) && !value.isBlank()) value = sharedStrings.get(Integer.parseInt(value));
                    values.set(column, value);
                }
                rows.add(values);
            }
            if (rows.isEmpty()) return List.of();
            List<String> headers = rows.get(0);
            List<Map<String, Object>> records = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) record.put(headers.get(j), j < rows.get(i).size() ? rows.get(i).get(j) : "");
                records.add(record);
            }
            return records;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid XLSX dataset", e);
        }
    }

    private List<String> readSharedStrings(ZipFile zip) throws Exception {
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return List.of();
        Document document = secureDocumentBuilderFactory().newDocumentBuilder().parse(zip.getInputStream(entry));
        NodeList items = document.getElementsByTagName("si");
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) strings.add(items.item(i).getTextContent());
        return strings;
    }

    private int columnIndex(String reference) {
        int index = 0;
        int i = 0;
        while (i < reference.length() && Character.isLetter(reference.charAt(i))) {
            index = index * 26 + Character.toUpperCase(reference.charAt(i)) - 'A' + 1;
            i++;
        }
        return Math.max(index - 1, 0);
    }

    private String toFieldSchema(List<DatasetImportRequest.FieldSchema> requested,
                                 List<Map<String, Object>> records) {
        try {
            if (requested != null && !requested.isEmpty()) return objectMapper.writeValueAsString(requested);
            if (records.isEmpty()) return "[]";
            List<Map<String, Object>> inferred = records.get(0).entrySet().stream().map(entry -> {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("fieldName", entry.getKey());
                field.put("fieldType", inferType(entry.getValue()));
                field.put("nullable", records.stream().anyMatch(record -> record.get(entry.getKey()) == null));
                return field;
            }).toList();
            return objectMapper.writeValueAsString(inferred);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize field schema", e);
        }
    }

    private String inferType(Object value) {
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Integer || value instanceof Long) return "integer";
        if (value instanceof Number) return "number";
        if (value instanceof Map) return "object";
        if (value instanceof List) return "array";
        return "string";
    }
}
