package com.cn.cloudpictureplatform.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 分页请求工厂类
 * 提供统一的分页参数处理逻辑，避免在每个服务中重复编写范围限制代码
 * 
 * @author Cloud Picture Platform Team
 * @since Java 21
 */
public final class PageRequestFactory {
    
    // 默认分页大小限制
    private static final int DEFAULT_PAGE_SIZE_MIN = 1;
    private static final int DEFAULT_PAGE_SIZE_MAX = 100;
    
    // 导出场景的分页大小限制
    private static final int EXPORT_PAGE_SIZE_MAX = 10000;
    
    private PageRequestFactory() {
        // 工具类禁止实例化
    }
    
    /**
     * 创建默认的 PageRequest
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return PageRequest 对象
     */
    public static PageRequest of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }
    
    /**
     * 创建带排序的 PageRequest
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sort 排序规则
     * @return PageRequest 对象
     */
    public static PageRequest of(int page, int size, Sort sort) {
        int pageIndex = Math.max(0, page);
        int pageSize = clampToDefaultRange(size);
        return PageRequest.of(pageIndex, pageSize, sort);
    }
    
    /**
     * 创建按指定字段降序排序的 PageRequest
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     * @return PageRequest 对象
     */
    public static PageRequest ofDescending(int page, int size, String sortBy) {
        return of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
    }
    
    /**
     * 创建按指定字段升序排序的 PageRequest
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     * @return PageRequest 对象
     */
    public static PageRequest ofAscending(int page, int size, String sortBy) {
        return of(page, size, Sort.by(Sort.Direction.ASC, sortBy));
    }
    
    /**
     * 创建用于导出的 PageRequest（支持更大的分页大小）
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param maxExportSize 最大导出大小（建议不超过 10000）
     * @return PageRequest 对象
     */
    public static PageRequest forExport(int page, int size, int maxExportSize) {
        int pageIndex = Math.max(0, page);
        int pageSize = clampToExportRange(size, maxExportSize);
        return PageRequest.of(pageIndex, pageSize, Sort.unsorted());
    }
    
    /**
     * 创建用于导出的 PageRequest（使用默认最大值 10000）
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return PageRequest 对象
     */
    public static PageRequest forExport(int page, int size) {
        return forExport(page, size, EXPORT_PAGE_SIZE_MAX);
    }
    
    /**
     * 获取默认的分页大小上限
     * 
     * @return 默认分页大小上限
     */
    public static int getDefaultMaxPageSize() {
        return DEFAULT_PAGE_SIZE_MAX;
    }
    
    /**
     * 获取导出场景的分页大小上限
     * 
     * @return 导出场景分页大小上限
     */
    public static int getExportMaxPageSize() {
        return EXPORT_PAGE_SIZE_MAX;
    }
    
    /**
     * 将分页大小限制到默认范围 [1, 100]
     * 
     * @param value 原始分页大小
     * @return 限制后的分页大小
     */
    private static int clampToDefaultRange(int value) {
        return Math.clamp(value, DEFAULT_PAGE_SIZE_MIN, DEFAULT_PAGE_SIZE_MAX);
    }
    
    /**
     * 将分页大小限制到导出范围 [1, maxExportSize]，其中 maxExportSize 不超过 EXPORT_PAGE_SIZE_MAX
     * 
     * @param value 原始分页大小
     * @param maxExportSize 自定义最大值
     * @return 限制后的分页大小
     */
    private static int clampToExportRange(int value, int maxExportSize) {
        return Math.clamp(value, DEFAULT_PAGE_SIZE_MIN, Math.min(maxExportSize, EXPORT_PAGE_SIZE_MAX));
    }
}
