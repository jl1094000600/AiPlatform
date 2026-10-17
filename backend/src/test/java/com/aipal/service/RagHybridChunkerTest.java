package com.aipal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagHybridChunkerTest {

    private final RagHybridChunker chunker = new RagHybridChunker(new RagChunker(), new ObjectMapper(), null);

    @Test
    void keepsArticleAndTableTitlesWithMarkdownTable() {
        String content = """
                # Q1 销售复盘

                本文总结一季度销售情况。

                表1 区域销售明细
                | 区域 | 销售额 | 负责人 |
                | --- | --- | --- |
                | 华东 | 120万 | Alice |
                | 华南 | 98万 | Bob |
                """;

        List<String> chunks = chunker.chunk(content, "DOCUMENT", 400, 40, null).chunks();

        String joined = String.join("\n---\n", chunks);
        assertTrue(joined.contains("文章标题：Q1 销售复盘"));
        assertTrue(joined.contains("表格标题：表1 区域销售明细"));
        assertTrue(joined.contains("| 区域 | 销售额 | 负责人 |"));
    }

    @Test
    void repeatsTableContextWhenLargeTableIsSplit() {
        StringBuilder table = new StringBuilder("""
                Q1 销售复盘

                表1 区域销售明细
                | 区域 | 销售额 | 负责人 |
                | --- | --- | --- |
                """);
        for (int i = 0; i < 12; i++) {
            table.append("| 区域").append(i).append(" | ")
                    .append(100 + i).append("万 | owner").append(i).append(" |\n");
        }

        List<String> chunks = chunker.chunk(table.toString(), "DOCUMENT", 180, 20, null).chunks();

        assertTrue(chunks.size() > 1);
        for (String item : chunks) {
            assertTrue(item.contains("表格标题：表1 区域销售明细"));
            assertTrue(item.contains("| 区域 | 销售额 | 负责人 |"));
        }
    }

    @Test
    void detectsCodeContentType() {
        String code = """
                public class Demo {
                    public void run() {
                        if (true) {
                            System.out.println("ok");
                        }
                    }
                }
                """;

        assertEquals("CODE", chunker.chunk(code, "AUTO", 300, 20, null).contentType());
    }

    @Test
    void preservesRealProseAndRepeatsMetadataOnlyForTableChunks() {
        StringBuilder content = new StringBuilder("""
                # Q1 销售复盘

                本文总结一季度销售情况，正文必须完整保留。

                表1 区域销售明细
                | 区域 | 销售额 | 负责人 |
                | --- | --- | --- |
                """);
        for (int i = 0; i < 12; i++) content.append("| 区域").append(i).append(" | 100万 | owner").append(i).append(" |\n");
        List<String> chunks = chunker.chunk(content.toString(), "DOCUMENT", 180, 20, null).chunks();
        assertTrue(String.join("\n", chunks).contains("本文总结一季度销售情况，正文必须完整保留。"));
        List<String> tableChunks = chunks.stream().filter(item -> item.contains("| 区域 | 销售额 | 负责人 |")).toList();
        assertTrue(tableChunks.size() > 1);
        for (String item : tableChunks) {
            assertTrue(item.contains("文章标题：Q1 销售复盘"));
            assertTrue(item.contains("表格标题：表1 区域销售明细"));
        }
        for (int i = 0; i < 12; i++) assertTrue(String.join("\n", tableChunks).contains("| 区域" + i + " | 100万 | owner" + i + " |"));
    }
}
