package com.cn.cloudpictureplatform.rag.interfaces.dto;

import java.util.List;

public record QueryResponse(String answer, List<String> citations, int chunksUsed) {}
