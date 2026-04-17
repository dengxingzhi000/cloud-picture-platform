---
name: scm-xlsx
description: SCM平台Excel报表生成和数据导出技能。用于导出供应链数据(库存报表、订单明细、财务对账、供应商绩效)到Excel格式，支持公式、格式化、数据透视表。遵循财务模型标准和多租户隔离。
tools: Bash, Read, Write, Grep
model: inherit
---

# SCM Excel 报表生成技能

为 SCM 供应链管理平台提供专业的 Excel 数据导出和报表生成能力。

## 核心原则

### 零公式错误
所有生成的 Excel 文件必须没有 #REF!、#DIV/0!、#VALUE!、#N/A、#NAME? 等错误。

### 财务模型颜色标准

- **蓝色文本**: 用户可修改的输入参数
- **黑色文本**: 公式和自动计算
- **绿色文本**: 工作表内部链接
- **红色文本**: 外部文件链接
- **黄色背景**: 需要特别关注的关键假设

### 数字格式规范

- **年份**: 显示为文本 ("2025")
- **货币**: 单位在列标题 ("营收 (万元)")
- **零值**: 显示为横线 "-"
- **百分比**: 保留一位小数 "85.0%"
- **负数**: 使用括号 "(1,234)"

### 关键规则

**永远使用 Excel 公式，不要硬编码计算值。** 例如使用 `=SUM(B2:B9)` 而不是 Python 计算后写入固定值。这确保 Excel 保持动态和可更新。

## 业务报表类型

### 1. 库存报表 (scm-inventory)

**实时库存明细表**
```python
import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils.dataframe import dataframe_to_rows

def generate_inventory_report(tenant_id: str, warehouse_id: str, output_path: str):
    """
    生成实时库存明细表

    数据来源: db_inventory.inv_stock
    关键字段:
    - sku_id: SKU ID
    - warehouse_id: 仓库ID
    - available_stock: 可用库存
    - locked_stock: 锁定库存（预留订单）
    - total_stock: 总库存 (公式: =可用库存+锁定库存)
    - reorder_point: 再订货点
    - status: 库存状态 (正常/低库存/缺货)
    """

    # 1. 从数据库读取数据（通过 API 调用 Java 服务）
    df = fetch_inventory_data(tenant_id, warehouse_id)

    # 2. 创建 Excel 工作簿
    wb = Workbook()
    ws = wb.active
    ws.title = "库存明细"

    # 3. 写入标题行
    headers = [
        "SKU编码", "商品名称", "仓库", "可用库存", "锁定库存",
        "总库存", "再订货点", "库存状态", "最后更新时间"
    ]
    ws.append(headers)

    # 设置标题样式
    header_fill = PatternFill(start_color="4F81BD", end_color="4F81BD", fill_type="solid")
    header_font = Font(bold=True, color="FFFFFF")

    for cell in ws[1]:
        cell.fill = header_fill
        cell.font = header_font
        cell.alignment = Alignment(horizontal="center")

    # 4. 写入数据行
    for idx, row in df.iterrows():
        row_num = idx + 2  # Excel 行号（从2开始）

        ws.append([
            row['sku_code'],
            row['product_name'],
            row['warehouse_name'],
            row['available_stock'],
            row['locked_stock'],
            None,  # 总库存用公式
            row['reorder_point'],
            None,  # 状态用公式
            row['updated_at']
        ])

        # 5. 添加公式（关键！）
        # F列: 总库存 = 可用库存 + 锁定库存
        ws[f'F{row_num}'] = f'=D{row_num}+E{row_num}'
        ws[f'F{row_num}'].font = Font(color="000000")  # 黑色 = 公式

        # H列: 库存状态（基于库存与再订货点对比）
        ws[f'H{row_num}'] = (
            f'=IF(F{row_num}=0,"缺货",'
            f'IF(F{row_num}<G{row_num},"低库存","正常"))'
        )
        ws[f'H{row_num}'].font = Font(color="000000")

        # 条件格式化: 缺货显示红色
        if ws[f'H{row_num}'].value == "缺货":
            ws[f'H{row_num}'].fill = PatternFill(
                start_color="FF0000", end_color="FF0000", fill_type="solid"
            )

    # 6. 调整列宽
    ws.column_dimensions['A'].width = 15
    ws.column_dimensions['B'].width = 30
    ws.column_dimensions['C'].width = 15

    # 7. 添加汇总行
    summary_row = len(df) + 2
    ws[f'A{summary_row}'] = "合计"
    ws[f'A{summary_row}'].font = Font(bold=True)

    ws[f'D{summary_row}'] = f'=SUM(D2:D{summary_row-1})'  # 可用库存总计
    ws[f'E{summary_row}'] = f'=SUM(E2:E{summary_row-1})'  # 锁定库存总计
    ws[f'F{summary_row}'] = f'=SUM(F2:F{summary_row-1})'  # 总库存

    # 8. 保存
    wb.save(output_path)

    # 9. 强制重算公式（使用 LibreOffice）
    import subprocess
    subprocess.run([
        "python3", "/scripts/recalc.py", output_path
    ])
```

**库存周转率分析**
```python
def generate_inventory_turnover_report(tenant_id: str, start_date: str, end_date: str):
    """
    库存周转率分析报表

    关键指标:
    - 期初库存
    - 期末库存
    - 平均库存 = (期初 + 期末) / 2
    - 销售成本（从订单数据）
    - 库存周转率 = 销售成本 / 平均库存
    - 周转天数 = 365 / 周转率
    """

    # 数据透视表示例
    pivot_data = create_pivot_table(
        source_data=inventory_transactions,
        rows=['product_category', 'warehouse'],
        columns=['month'],
        values='turnover_rate',
        aggfunc='mean'
    )
```

### 2. 订单报表 (scm-order)

**订单明细导出**
```python
def export_order_details(tenant_id: str, date_range: tuple, output_path: str):
    """
    导出订单明细

    数据来源: db_order.ord_order (分区表，按 create_time 分区)
    关键信息:
    - order_no: 订单号（UNIQUE 约束包含 create_time）
    - customer_info: 客户信息
    - order_items: 订单明细（多行）
    - payment_status: 支付状态
    - logistics_status: 物流状态
    - order_state: 订单状态（状态机管理）
    """

    wb = Workbook()

    # Sheet 1: 订单汇总
    ws_summary = wb.active
    ws_summary.title = "订单汇总"

    # ... 写入汇总数据

    # Sheet 2: 订单明细
    ws_detail = wb.create_sheet("订单明细")

    # 关键: 订单状态使用颜色编码
    status_colors = {
        'PENDING_PAYMENT': 'FFFF00',     # 黄色
        'PAID': '00FF00',                 # 绿色
        'SHIPPED': '0000FF',              # 蓝色
        'DELIVERED': '00AA00',            # 深绿
        'CANCELLED': 'FF0000',            # 红色
    }

    for order in orders:
        # ... 写入订单数据
        status_cell = ws_detail.cell(row=row_num, column=status_col)
        status_cell.value = order['order_state']
        status_cell.fill = PatternFill(
            start_color=status_colors[order['order_state']],
            fill_type="solid"
        )

    wb.save(output_path)
```

**日销售汇总报表**
```python
def generate_daily_sales_report(tenant_id: str, date: str):
    """
    日销售汇总

    关键指标:
    - 订单数量
    - 订单金额（使用 BigDecimal，Excel 中保留两位小数）
    - 平均客单价 = 总金额 / 订单数
    - 退款金额
    - 实收金额 = 总金额 - 退款金额
    """

    # 数字格式化
    for cell in ws['D']:  # 金额列
        cell.number_format = '#,##0.00'  # 千分位 + 两位小数

    for cell in ws['E']:  # 百分比列
        cell.number_format = '0.0%'
```

### 3. 财务对账单 (scm-finance)

**供应商对账单**
```python
def generate_supplier_reconciliation(tenant_id: str, supplier_id: str, month: str):
    """
    供应商对账单

    数据来源:
    - db_purchase: 采购订单
    - db_finance: 付款记录

    关键信息:
    - 期初应付款余额
    - 本期采购金额
    - 本期付款金额
    - 期末应付款余额
    - 账龄分析
    """

    # 使用公式确保准确性
    # 期末余额 = 期初余额 + 采购金额 - 付款金额
    ws['E10'] = '=B10+C10-D10'

    # 重要: 使用 BigDecimal，不要用 float
    # Java 传递时已格式化为字符串 "12345.67"
```

**月度财务报表**
```python
def generate_monthly_financial_report(tenant_id: str, year_month: str):
    """
    月度财务报表（多Sheet）

    Sheet 1: 损益表
    Sheet 2: 现金流量表
    Sheet 3: 应收账款明细
    Sheet 4: 应付账款明细
    Sheet 5: 库存价值评估

    关键公式:
    - 毛利润 = 营收 - 成本
    - 毛利率 = 毛利润 / 营收
    - 净利润 = 毛利润 - 费用
    """

    # 使用命名范围便于维护
    wb.define_name('Revenue', '=损益表!$B$10')
    wb.define_name('Cost', '=损益表!$B$11')

    # 公式引用命名范围
    ws['B12'] = '=Revenue-Cost'  # 毛利润
    ws['B12'].font = Font(color="000000")  # 黑色 = 公式
```

### 4. 供应商绩效报表 (scm-supplier)

**供应商评分卡**
```python
def generate_supplier_scorecard(tenant_id: str, quarter: str):
    """
    供应商季度评分卡

    评估维度:
    - 交付及时率
    - 质量合格率
    - 价格竞争力
    - 响应速度
    - 综合评分（加权平均）

    使用雷达图展示多维评分
    """

    from openpyxl.chart import RadarChart, Reference

    chart = RadarChart()
    chart.title = "供应商绩效雷达图"

    # 数据范围
    labels = Reference(ws, min_col=1, min_row=2, max_row=6)
    data = Reference(ws, min_col=2, min_row=1, max_row=6)

    chart.add_data(data, titles_from_data=True)
    chart.set_categories(labels)

    ws.add_chart(chart, "E5")
```

### 5. 物流配送报表 (scm-logistics)

**配送效率分析**
```python
def generate_logistics_performance_report(tenant_id: str, date_range: tuple):
    """
    配送效率分析报表

    数据来源: db_logistics.tms_waybill

    关键指标:
    - 运单数量
    - 准时率 = 准时送达数 / 总运单数
    - 平均配送时效（小时）
    - 配送成本
    - 单位成本 = 总成本 / 运单数
    - 异常率
    """

    # 使用条件格式突出显示异常
    from openpyxl.formatting.rule import CellIsRule

    red_fill = PatternFill(start_color="FF0000", fill_type="solid")

    # 准时率 < 90% 标红
    ws.conditional_formatting.add(
        'D2:D100',
        CellIsRule(operator='lessThan', formula=['0.9'], fill=red_fill)
    )
```

## SCM 平台集成

### Java 服务调用

```java
@Service
public class ReportExportService {

    /**
     * 异步导出库存报表
     */
    @Async
    @DS("inventory")
    public CompletableFuture<String> exportInventoryReport(String tenantId, String warehouseId) {

        return CompletableFuture.supplyAsync(() -> {
            // 1. 查询库存数据
            List<InvStock> stocks = invStockMapper.selectByWarehouse(
                tenantId, warehouseId
            );

            // 2. 转换为 JSON（传递给 Python）
            String jsonData = JsonUtils.toJson(stocks);

            // 3. 调用 Python 脚本生成 Excel
            String outputPath = String.format(
                "/data/reports/%s/inventory_%s_%s.xlsx",
                tenantId, warehouseId, LocalDate.now()
            );

            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                "/scripts/generate_inventory_report.py",
                "--data", jsonData,
                "--output", outputPath
            );

            try {
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    throw new BusinessException("报表生成失败");
                }

                // 4. 重算公式（必须！）
                recalculateFormulas(outputPath);

                // 5. 审计日志
                auditLogService.log("INVENTORY_REPORT_EXPORTED", tenantId);

                return outputPath;

            } catch (Exception e) {
                log.error("导出报表失败", e);
                throw new BusinessException("报表导出异常");
            }
        }, Executors.newVirtualThreadPerTaskExecutor());  // 使用虚拟线程
    }

    /**
     * 重算 Excel 公式（关键步骤）
     */
    private void recalculateFormulas(String excelPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "python3",
            "/scripts/recalc.py",
            excelPath
        );
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("公式重算失败");
        }
    }
}
```

### 批量导出场景

```java
/**
 * 定时任务: 每天凌晨生成前一日销售报表
 */
@Component
public class DailySalesReportJob {

    @XxlJob("dailySalesReportJobHandler")
    public void execute() {
        // 获取所有租户
        List<Tenant> tenants = tenantService.listAllActiveTenants();

        // 并发生成报表（虚拟线程）
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> tasks = tenants.stream()
                .map(tenant -> CompletableFuture.runAsync(
                    () -> {
                        String reportPath = reportService.generateDailySalesReport(
                            tenant.getTenantId(),
                            LocalDate.now().minusDays(1)
                        );

                        // 发送邮件通知
                        emailService.send(
                            tenant.getEmail(),
                            "日销售报表",
                            "报表已生成: " + reportPath
                        );
                    },
                    executor
                ))
                .toList();

            // 等待所有任务完成
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        }
    }
}
```

## 配置

### Python 依赖

```bash
pip install pandas openpyxl xlsxwriter jinja2

# 公式重算需要 LibreOffice
apt-get install libreoffice-calc
```

### 存储配置

```yaml
scm:
  report:
    storage:
      base-path: /data/reports
      tenant-isolation: true
      max-file-size: 50MB
      retention-days: 90

    export:
      async: true
      timeout: 300s  # 5分钟超时
      max-concurrent: 10
```

## 质量检查清单

导出 Excel 前必须验证：

- [ ] 所有公式都正确，无 #REF! 等错误
- [ ] 数字格式正确（货币、百分比、日期）
- [ ] 颜色标准符合财务模型规范
- [ ] 汇总行公式正确（SUM、AVERAGE）
- [ ] 已执行 recalc.py 重算公式
- [ ] 文件大小合理（< 50MB）
- [ ] 租户数据隔离正确
- [ ] BigDecimal 金额保留两位小数

## 性能优化

1. **分批写入**: 大数据量使用 `openpyxl` 的 `write_only` 模式
2. **压缩**: 使用 XLSX 压缩减小文件大小
3. **缓存**: 缓存常用的报表模板
4. **异步导出**: 大报表异步生成，通知用户下载
5. **虚拟线程**: 使用 Java 21 虚拟线程并发导出多租户报表

## 常见问题

**Q: Excel 打开后公式不生效？**
A: 必须执行 `recalc.py` 脚本重算公式，或在 openpyxl 中设置：
```python
wb.calculation.calcMode = 'auto'
```

**Q: BigDecimal 金额精度丢失？**
A: 不要用 `float()`，直接传字符串：
```python
ws['B2'] = str(amount)  # "12345.67"
ws['B2'].number_format = '#,##0.00'
```

**Q: 导出大量数据（10万行）很慢？**
A: 使用 `write_only` 模式：
```python
wb = Workbook(write_only=True)
ws = wb.create_sheet()
for row in data:
    ws.append(row)
```

## 扩展阅读

- `scm-inventory/service` - 库存服务
- `scm-order/service` - 订单服务
- `scm-finance/service` - 财务服务
- `CLAUDE.md` - PostgreSQL 分区表约束说明