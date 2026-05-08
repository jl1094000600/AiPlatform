-- RAG ingestion records

CREATE TABLE IF NOT EXISTS rag_ingestion_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    collection_name VARCHAR(128) NOT NULL COMMENT 'Chroma集合名称',
    document_title VARCHAR(255) NOT NULL COMMENT '文档标题',
    embedding_model_id BIGINT NOT NULL COMMENT 'Embedding模型ID',
    embedding_model_code VARCHAR(128) NOT NULL COMMENT 'Embedding模型编码',
    chroma_url VARCHAR(255) NOT NULL COMMENT 'Chroma服务地址',
    chunk_size INT NOT NULL COMMENT '分块大小',
    chunk_overlap INT NOT NULL COMMENT '分块重叠',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数量',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    error_message VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rag_collection_time (collection_name, create_time),
    KEY idx_rag_model_time (embedding_model_id, create_time),
    KEY idx_rag_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG文档入库记录表';
