package com.github.agentdock.core.model;

/** 已由宿主解析、可供模型参考的附件正文。 */
public record AttachmentContent(String fileId, String fileName, String contentType, String content, boolean truncated) { }
