package com.aipal.agent.image.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FileParseService {

    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();

    public Map<String, Object> parseFile(String inputData, String inputType, String fileTypeHint) {
        Map<String, Object> result = new HashMap<>();

        try {
            byte[] fileContent = getFileContent(inputData, inputType);
            String contentType = fileTypeHint != null && !fileTypeHint.equalsIgnoreCase("unknown")
                    ? "application/" + fileTypeHint
                    : tika.detect(fileContent);

            String extractedText = extractText(fileContent, contentType);

            result.put("content", extractedText);
            result.put("contentType", contentType);
            result.put("length", fileContent.length);
            result.put("fileType", fileTypeHint);

            log.info("Successfully parsed file, content length: {}", extractedText.length());
            return result;

        } catch (Exception e) {
            log.error("Failed to parse file", e);
            result.put("error", e.getMessage());
            result.put("content", "");
            return result;
        }
    }

    private byte[] getFileContent(String inputData, String inputType) throws IOException {
        if ("url".equalsIgnoreCase(inputType)) {
            RestTemplate restTemplate = new RestTemplate();
            byte[] imageBytes = restTemplate.getForObject(inputData, byte[].class);
            return imageBytes != null ? imageBytes : new byte[0];
        } else if ("base64".equalsIgnoreCase(inputType)) {
            String base64Data = inputData;
            if (inputData.contains(",")) {
                base64Data = inputData.split(",")[1];
            }
            return Base64.getDecoder().decode(base64Data);
        } else {
            return inputData.getBytes();
        }
    }

    private String extractText(byte[] content, String contentType) {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();

        } catch (IOException | TikaException | org.xml.sax.SAXException e) {
            log.error("Text extraction failed", e);
            return "";
        }
    }

    public String detectFileType(byte[] content) {
        return tika.detect(content);
    }
}
