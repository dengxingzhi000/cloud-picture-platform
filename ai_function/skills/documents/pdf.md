---
name: scm-pdf
description: SCM平台PDF文档生成和处理技能。用于生成业务文档(发票、采购订单、合同、物流单据)以及提取和分析PDF内容。针对供应链管理场景优化，支持多租户环境。
tools: Bash, Read, Write, Grep
model: inherit
---

# SCM PDF 文档处理技能

为 SCM 供应链管理平台提供专业的 PDF 文档生成和处理能力。

## 核心能力

### 1. 业务文档生成

**发票生成** (scm-finance)
```python
# 基于 Invoice 实体生成正式发票
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

def generate_invoice_pdf(invoice: Invoice, output_path: str):
    """
    生成标准发票 PDF

    必须包含:
    - 租户信息 (tenant_id, tenant_name)
    - 发票号 (invoice_no) - 唯一标识
    - 开票日期、到期日期
    - 金额使用 BigDecimal，格式化为两位小数
    - 买方/卖方信息
    - 明细项列表
    - 税额计算
    - 公司印章区域
    """
    c = canvas.Canvas(output_path, pagesize=A4)

    # 标题
    c.setFont("Helvetica-Bold", 20)
    c.drawString(200, 800, f"发票 Invoice")

    # 发票信息
    c.setFont("Helvetica", 12)
    c.drawString(50, 750, f"发票号: {invoice.invoice_no}")
    c.drawString(50, 730, f"开票日期: {invoice.issue_date}")
    c.drawString(50, 710, f"租户: {invoice.tenant_id}")

    # 金额 - 必须使用 BigDecimal 转换后的字符串
    c.drawString(50, 680, f"总金额: ¥{invoice.total_amount}")

    c.save()
```

**采购订单** (scm-purchase)
```python
def generate_purchase_order_pdf(order: PurOrder, output_path: str):
    """
    生成采购订单 PDF

    关键要素:
    - 订单号 (order_no) - 唯一标识，包含 create_time 用于分区表约束
    - 供应商信息 (supplier_id, supplier_name)
    - 采购明细 (sku_id, quantity, unit_price)
    - 总金额 (BigDecimal)
    - 交货日期、收货地址
    - 审批状态和审批人
    """
    pass  # 实现类似结构
```

**合同文档** (scm-purchase)
```python
def generate_contract_pdf(contract: PurContract, output_path: str):
    """
    生成采购合同 PDF

    必须包含:
    - 合同编号、签订日期、有效期
    - 甲乙方信息
    - 条款内容 (从数据库读取)
    - 签字盖章区域
    - 附件清单
    """
    pass
```

**物流运单** (scm-logistics)
```python
def generate_waybill_pdf(waybill: TmsWaybill, output_path: str):
    """
    生成物流运单 PDF

    包含:
    - 运单号 (waybill_no)
    - 发货人/收货人信息
    - 货物清单
    - 运输路线
    - 条形码/二维码 (用于扫描追踪)
    """
    pass
```

### 2. PDF 内容提取

**提取供应商发票信息**
```python
import pdfplumber

def extract_supplier_invoice(pdf_path: str) -> dict:
    """
    从供应商提供的 PDF 发票中提取结构化数据

    返回格式:
    {
        'invoice_no': str,
        'issue_date': str,
        'supplier_name': str,
        'total_amount': Decimal,
        'line_items': [
            {'description': str, 'quantity': int, 'price': Decimal}
        ]
    }
    """
    with pdfplumber.open(pdf_path) as pdf:
        first_page = pdf.pages[0]

        # 提取文本并保留布局
        text = first_page.extract_text(layout=True)

        # 提取表格数据
        tables = first_page.extract_tables()

        # 解析关键字段
        # ... 正则表达式匹配发票号、日期、金额等

    return parsed_data
```

**批量处理合同扫描件**
```python
def batch_extract_contracts(pdf_dir: str) -> list:
    """
    批量提取合同扫描件的关键信息

    使用 OCR 处理扫描的 PDF:
    - pytesseract 识别文字
    - 提取合同号、签订日期、关键条款
    - 用于合同归档和检索
    """
    pass
```

### 3. PDF 合并与拆分

**合并订单附件**
```python
from pypdf import PdfMerger

def merge_order_attachments(order_id: str, attachment_paths: list, output_path: str):
    """
    合并订单相关的多个 PDF 附件

    场景:
    - 合并采购申请单 + 审批单 + 报价单
    - 合并发票 + 付款凭证 + 收据
    - 用于归档和审计
    """
    merger = PdfMerger()

    for pdf_path in attachment_paths:
        merger.append(pdf_path)

    merger.write(output_path)
    merger.close()
```

**拆分多页报表**
```python
from pypdf import PdfReader, PdfWriter

def split_report_by_tenant(report_path: str, output_dir: str):
    """
    将多租户报表按租户拆分

    场景:
    - 月度财务报表按租户拆分
    - 库存报表按仓库拆分
    - 便于分发给不同租户
    """
    reader = PdfReader(report_path)

    # 假设报表按租户分页
    for i, page in enumerate(reader.pages):
        writer = PdfWriter()
        writer.add_page(page)

        output_file = f"{output_dir}/tenant_{i+1}_report.pdf"
        with open(output_file, 'wb') as f:
            writer.write(f)
```

## SCM 平台集成指南

### 与 scm-finance 集成

```java
@Service
public class InvoiceService {

    @Autowired
    private InvoiceMapper invoiceMapper;

    /**
     * 生成发票并返回 PDF 路径
     *
     * @param invoiceId 发票ID
     * @return PDF文件路径
     */
    @DS("finance")  // 多租户数据源路由
    public String generateInvoicePDF(Long invoiceId) {
        // 1. 查询发票数据
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BusinessException("发票不存在");
        }

        // 2. 验证租户权限
        TenantValidationUtil.validate(invoice.getTenantId());

        // 3. 调用 Python 脚本生成 PDF
        String outputPath = "/data/invoices/" + invoice.getInvoiceNo() + ".pdf";

        ProcessBuilder pb = new ProcessBuilder(
            "python3",
            "/scripts/generate_invoice.py",
            "--invoice-id", invoiceId.toString(),
            "--output", outputPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new BusinessException("PDF生成失败");
        }

        // 4. 记录审计日志
        auditLogService.log("INVOICE_PDF_GENERATED", invoiceId);

        return outputPath;
    }
}
```

### 与 scm-purchase 集成

```java
@Service
public class PurchaseOrderService {

    /**
     * 生成采购订单 PDF
     *
     * 注意: PurOrder 使用分区表，UNIQUE 约束包含 create_time
     */
    @DS("purchase")
    @GlobalTransactional(name = "generate-po-pdf", rollbackFor = Exception.class)
    public String generatePurchaseOrderPDF(String orderNo) {
        // 查询订单（分区表查询需包含 create_time）
        PurOrder order = purOrderMapper.selectByOrderNo(orderNo);

        // 生成 PDF
        String pdfPath = callPdfGenerator(order);

        // 更新订单状态（已生成PDF）
        order.setPdfGenerated(true);
        purOrderMapper.updateById(order);

        return pdfPath;
    }
}
```

### 批量导出场景

```java
/**
 * 批量导出月度发票 PDF
 *
 * 使用场景: 财务月结，批量生成当月所有发票
 */
@Async
public void batchExportMonthlyInvoices(String tenantId, String month) {
    // 1. 查询当月所有发票
    List<Invoice> invoices = invoiceMapper.selectByMonth(tenantId, month);

    // 2. 并发生成 PDF（使用虚拟线程）
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<CompletableFuture<String>> futures = invoices.stream()
            .map(invoice -> CompletableFuture.supplyAsync(
                () -> generateInvoicePDF(invoice.getId()),
                executor
            ))
            .toList();

        // 3. 等待所有 PDF 生成完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    // 4. 通知用户（通过 scm-notify）
    notificationService.send(tenantId, "月度发票生成完成");
}
```

## 配置要求

### Python 环境

```bash
# 安装依赖
pip install reportlab pypdf pdfplumber pytesseract pandas

# OCR 支持（可选）
apt-get install tesseract-ocr tesseract-ocr-chi-sim
```

### 文件存储

```yaml
# application.yml
scm:
  pdf:
    storage:
      base-path: /data/pdf-storage
      structure: tenant-based  # 按租户隔离
      retention-days: 365      # 保留期限

    templates:
      invoice: /templates/invoice_template.pdf
      contract: /templates/contract_template.pdf
      waybill: /templates/waybill_template.pdf
```

### 多租户隔离

```
/data/pdf-storage/
├── tenant_001/
│   ├── invoices/
│   │   ├── INV20250116001.pdf
│   │   └── INV20250116002.pdf
│   ├── contracts/
│   └── waybills/
├── tenant_002/
│   └── ...
```

## 安全考虑

1. **文件访问控制**
   - 验证租户权限后才能访问 PDF
   - 使用 `TenantContextHolder` 获取当前租户
   - API 需要 JWT 认证

2. **敏感信息脱敏**
   - 生成对外 PDF 时脱敏敏感字段
   - 使用 `DesensitizeUtils` 处理手机号、身份证等

3. **审计日志**
   - 记录 PDF 生成、下载、删除操作
   - 集成 scm-audit 服务

## 性能优化

1. **异步生成**: 使用 `@Async` 和虚拟线程处理批量生成
2. **缓存模板**: 缓存常用的 PDF 模板减少 I/O
3. **压缩存储**: 生成的 PDF 自动压缩存储
4. **CDN 分发**: 高频访问的 PDF 上传到 CDN

## 工作流集成

在提交代码前确保：

```bash
# 测试 PDF 生成功能
mvn test -Dtest=InvoiceServiceTest#testGeneratePDF

# 验证文件大小合理（< 5MB）
ls -lh /tmp/test_invoice.pdf

# 检查 PDF 内容完整性
pdfinfo /tmp/test_invoice.pdf
```

## 常见问题

**Q: PDF 生成失败，报 FileNotFoundError**
A: 检查 `/data/pdf-storage` 目录权限，确保 Java 进程有写权限

**Q: 中文显示为乱码**
A: 安装中文字体包，在 reportlab 中指定字体：
```python
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
pdfmetrics.registerFont(TTFont('SimSun', 'simsun.ttc'))
```

**Q: BigDecimal 如何正确格式化到 PDF?**
A: 使用 `setScale` 保留两位小数：
```java
String formattedAmount = invoice.getTotalAmount()
    .setScale(2, RoundingMode.HALF_UP)
    .toString();
```

## 扩展阅读

- `scm-finance/service` - 发票业务逻辑
- `scm-purchase/service` - 采购订单和合同管理
- `scm-common/core/DesensitizeUtils` - 数据脱敏工具
- `docs/multi-tenant/` - 多租户架构文档