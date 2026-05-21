---
name: scm-docx
description: SCM平台Word文档生成和编辑技能。用于创建和编辑技术文档(架构设计、API文档、需求规格)、业务文档(采购合同、服务协议、流程手册)以及变更追踪。支持模板渲染、批注和审阅工作流。
tools: Bash, Read, Write, Grep
model: inherit
---

# SCM Word 文档处理技能

为 SCM 供应链管理平台提供专业的 Word 文档创建、编辑和审阅能力。

## 核心能力

### 1. 技术文档生成

**架构设计文档 (ADR)**
```python
from docx import Document
from docx.shared import Pt, Inches, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH

def generate_architecture_decision_record(adr_data: dict, output_path: str):
    """
    生成架构决策记录 (Architecture Decision Record)

    使用场景:
    - 记录 SCM 平台的重大技术决策
    - 分布式事务方案选型（Seata AT vs TCC）
    - 多租户数据库隔离策略
    - 缓存架构（Caffeine + Redis）

    ADR 结构:
    1. 标题和编号
    2. 背景和问题陈述
    3. 决策内容
    4. 后果和权衡
    5. 相关人员和日期
    """

    doc = Document()

    # 标题
    title = doc.add_heading(f'ADR-{adr_data["number"]}: {adr_data["title"]}', level=1)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER

    # 元数据表格
    table = doc.add_table(rows=4, cols=2)
    table.style = 'Light Grid Accent 1'

    metadata = [
        ('状态', adr_data['status']),
        ('决策日期', adr_data['date']),
        ('决策者', adr_data['decision_maker']),
        ('影响范围', adr_data['scope'])
    ]

    for i, (key, value) in enumerate(metadata):
        table.rows[i].cells[0].text = key
        table.rows[i].cells[1].text = value

    # 背景和问题
    doc.add_heading('背景和问题', level=2)
    doc.add_paragraph(adr_data['context'])

    # 决策内容
    doc.add_heading('决策', level=2)
    doc.add_paragraph(adr_data['decision'])

    # 代码示例（如果有）
    if 'code_example' in adr_data:
        doc.add_heading('实现示例', level=3)
        code_para = doc.add_paragraph(adr_data['code_example'])
        code_para.style = 'Code'  # 使用代码样式

    # 后果
    doc.add_heading('后果', level=2)
    doc.add_paragraph('优点:', style='List Bullet')
    for pro in adr_data['pros']:
        doc.add_paragraph(pro, style='List Bullet 2')

    doc.add_paragraph('缺点:', style='List Bullet')
    for con in adr_data['cons']:
        doc.add_paragraph(con, style='List Bullet 2')

    # 保存
    doc.save(output_path)
```

**API 文档**
```python
def generate_api_documentation(service_name: str, api_specs: list, output_path: str):
    """
    生成服务 API 文档

    数据来源:
    - 从 Controller 类的注解提取
    - @RestController, @GetMapping, @ApiOperation

    结构:
    - 服务概述
    - 认证方式（JWT）
    - API 列表（按模块分组）
    - 请求/响应示例
    - 错误码说明
    """

    doc = Document()

    # 标题页
    doc.add_heading(f'{service_name} API 文档', level=1)
    doc.add_paragraph(f'版本: v1.0')
    doc.add_paragraph(f'生成时间: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')

    doc.add_page_break()

    # 认证说明
    doc.add_heading('认证方式', level=2)
    auth_text = doc.add_paragraph(
        '所有 API 请求需要在 HTTP Header 中携带 JWT Token:'
    )
    code = doc.add_paragraph('Authorization: Bearer <your-jwt-token>')
    code.style = 'Code'

    # API 列表
    for api in api_specs:
        doc.add_heading(api['name'], level=2)

        # API 元信息表格
        table = doc.add_table(rows=4, cols=2)
        table.style = 'Light List Accent 1'

        table.rows[0].cells[0].text = 'HTTP方法'
        table.rows[0].cells[1].text = api['method']

        table.rows[1].cells[0].text = 'URL'
        table.rows[1].cells[1].text = api['url']

        table.rows[2].cells[0].text = '描述'
        table.rows[2].cells[1].text = api['description']

        table.rows[3].cells[0].text = '权限要求'
        table.rows[3].cells[1].text = api['permission']

        # 请求参数
        doc.add_heading('请求参数', level=3)
        if api['parameters']:
            param_table = doc.add_table(rows=len(api['parameters'])+1, cols=5)
            param_table.style = 'Light Grid Accent 1'

            # 表头
            headers = ['参数名', '类型', '必填', '说明', '示例']
            for i, header in enumerate(headers):
                param_table.rows[0].cells[i].text = header

            # 参数行
            for i, param in enumerate(api['parameters']):
                param_table.rows[i+1].cells[0].text = param['name']
                param_table.rows[i+1].cells[1].text = param['type']
                param_table.rows[i+1].cells[2].text = '是' if param['required'] else '否'
                param_table.rows[i+1].cells[3].text = param['description']
                param_table.rows[i+1].cells[4].text = param['example']

        # 响应示例
        doc.add_heading('响应示例', level=3)
        response_para = doc.add_paragraph(json.dumps(api['response_example'], indent=2, ensure_ascii=False))
        response_para.style = 'Code'

    doc.save(output_path)
```

**需求规格说明书 (SRS)**
```python
def generate_requirements_specification(feature_name: str, requirements: dict):
    """
    生成需求规格说明书

    使用场景:
    - 新功能开发前的需求文档
    - 例: "库存预警功能需求规格"

    结构:
    1. 功能概述
    2. 用户故事
    3. 功能需求（详细）
    4. 非功能需求（性能、安全等）
    5. 数据模型
    6. 接口设计
    7. 验收标准
    """

    doc = Document()

    # 封面
    doc.add_heading(f'{feature_name} 需求规格说明书', level=1)

    # 文档修订历史
    doc.add_heading('修订历史', level=2)
    revision_table = doc.add_table(rows=len(requirements['revisions'])+1, cols=4)
    revision_table.style = 'Light Grid Accent 1'

    headers = ['版本', '日期', '作者', '修订内容']
    for i, header in enumerate(headers):
        revision_table.rows[0].cells[i].text = header

    for i, rev in enumerate(requirements['revisions']):
        revision_table.rows[i+1].cells[0].text = rev['version']
        revision_table.rows[i+1].cells[1].text = rev['date']
        revision_table.rows[i+1].cells[2].text = rev['author']
        revision_table.rows[i+1].cells[3].text = rev['changes']

    # 功能需求
    doc.add_heading('功能需求', level=2)

    for req in requirements['functional']:
        doc.add_heading(f'FR-{req["id"]}: {req["name"]}', level=3)
        doc.add_paragraph(f'优先级: {req["priority"]}')
        doc.add_paragraph(f'描述: {req["description"]}')

        # 验收标准
        doc.add_paragraph('验收标准:', style='List Bullet')
        for criterion in req['acceptance_criteria']:
            doc.add_paragraph(criterion, style='List Bullet 2')

    doc.save(f'/docs/{feature_name}_SRS.docx')
```

### 2. 业务文档生成

**采购合同**
```python
def generate_purchase_contract(contract_data: dict, output_path: str):
    """
    生成采购合同 Word 文档

    数据来源: db_purchase.pur_contract

    关键信息:
    - 合同编号（唯一）
    - 甲方（采购方）信息
    - 乙方（供应商）信息
    - 采购明细表格
    - 合同条款
    - 签字盖章区域
    """

    doc = Document('/templates/contract_template.docx')  # 使用模板

    # 替换占位符
    replace_text_in_doc(doc, '{{contract_no}}', contract_data['contract_no'])
    replace_text_in_doc(doc, '{{party_a}}', contract_data['buyer_name'])
    replace_text_in_doc(doc, '{{party_b}}', contract_data['supplier_name'])
    replace_text_in_doc(doc, '{{sign_date}}', contract_data['sign_date'])

    # 查找并填充采购明细表格
    for table in doc.tables:
        if table.rows[0].cells[0].text == '序号':  # 识别明细表
            # 添加采购项
            for i, item in enumerate(contract_data['items']):
                row = table.add_row()
                row.cells[0].text = str(i + 1)
                row.cells[1].text = item['product_name']
                row.cells[2].text = item['specification']
                row.cells[3].text = str(item['quantity'])
                row.cells[4].text = f"¥{item['unit_price']}"
                row.cells[5].text = f"¥{item['total_price']}"

    doc.save(output_path)

def replace_text_in_doc(doc, placeholder, value):
    """在文档中替换占位符"""
    for paragraph in doc.paragraphs:
        if placeholder in paragraph.text:
            paragraph.text = paragraph.text.replace(placeholder, value)
```

**服务协议 (SLA)**
```python
def generate_service_level_agreement(tenant_data: dict):
    """
    生成服务等级协议

    使用场景:
    - 与 SCM 平台租户签订的 SLA
    - 定义服务可用性、性能指标、支持响应时间

    内容:
    - 服务范围
    - 性能指标（99.9% 可用性）
    - 响应时间承诺
    - 维护窗口
    - 违约责任
    """
    pass  # 类似合同生成流程
```

**操作手册**
```python
def generate_operation_manual(module_name: str, procedures: list):
    """
    生成操作手册

    使用场景:
    - 用户操作指南
    - 管理员配置手册
    - 故障排查手册

    特点:
    - 包含截图（插入图片）
    - 步骤编号
    - 注意事项高亮
    """

    doc = Document()

    doc.add_heading(f'{module_name} 操作手册', level=1)

    for i, procedure in enumerate(procedures):
        doc.add_heading(f'{i+1}. {procedure["name"]}', level=2)

        for j, step in enumerate(procedure['steps']):
            # 步骤编号
            doc.add_paragraph(f'{i+1}.{j+1} {step["description"]}', style='List Number')

            # 插入截图（如果有）
            if 'screenshot' in step:
                doc.add_picture(step['screenshot'], width=Inches(5))

            # 注意事项（黄色高亮）
            if 'warning' in step:
                warning = doc.add_paragraph(f'⚠️ 注意: {step["warning"]}')
                run = warning.runs[0]
                run.font.color.rgb = RGBColor(255, 0, 0)  # 红色文字

    doc.save(f'/docs/manuals/{module_name}_manual.docx')
```

### 3. 文档审阅和变更追踪

**使用 Redlining 工作流编辑现有文档**
```python
from docx import Document
from docx.oxml.ns import qn

def edit_document_with_tracking(doc_path: str, changes: list, output_path: str):
    """
    使用追踪更改模式编辑文档

    工作流:
    1. 规划变更（markdown 记录）
    2. 批量编辑（3-10处/批）
    3. 实施变更（标记删除和插入）
    4. 验证（确保只标记实际变更的文字）

    关键原则: 只标记实际变更的文本
    """

    doc = Document(doc_path)

    # 启用追踪更改
    doc.settings.element.get_or_add_revisionView()

    for change in changes:
        # 查找需要修改的段落
        for paragraph in doc.paragraphs:
            if change['search_text'] in paragraph.text:

                # 标记删除
                for run in paragraph.runs:
                    if change['search_text'] in run.text:
                        # 添加删除标记
                        run._element.getparent().remove(run._element)

                # 插入新文本（带追踪）
                new_run = paragraph.add_run(change['replace_text'])
                new_run._element.set(qn('w:rsidR'), '00000001')  # 修订ID

    doc.save(output_path)
```

**添加批注**
```python
def add_comments_to_document(doc_path: str, comments: list):
    """
    为文档添加批注

    使用场景:
    - 代码审查意见
    - 需求澄清
    - 文档评审反馈
    """

    doc = Document(doc_path)

    for comment in comments:
        # 查找目标段落
        paragraph = find_paragraph_by_text(doc, comment['target_text'])

        if paragraph:
            # 添加批注
            # 注意: python-docx 不直接支持批注，需使用 lxml 操作 XML
            # 或使用 pywin32 调用 Word COM 接口
            pass
```

### 4. 文档转换

**DOCX → PDF**
```bash
# 使用 LibreOffice 转换
libreoffice --headless --convert-to pdf --outdir /output /input/document.docx
```

**DOCX → Markdown**
```bash
# 使用 pandoc
pandoc input.docx -o output.md
```

## SCM 平台集成

### 自动生成合同文档

```java
@Service
public class ContractDocumentService {

    /**
     * 生成采购合同 Word 文档
     */
    @DS("purchase")
    @GlobalTransactional(name = "generate-contract", rollbackFor = Exception.class)
    public String generateContractDocument(Long contractId) {

        // 1. 查询合同数据
        PurContract contract = purContractMapper.selectById(contractId);

        // 2. 查询采购明细
        List<PurContractItem> items = purContractItemMapper.selectByContractId(contractId);

        // 3. 准备数据
        Map<String, Object> contractData = new HashMap<>();
        contractData.put("contract_no", contract.getContractNo());
        contractData.put("buyer_name", contract.getBuyerName());
        contractData.put("supplier_name", contract.getSupplierName());
        contractData.put("sign_date", contract.getSignDate().toString());
        contractData.put("items", items);

        // 4. 调用 Python 生成 DOCX
        String outputPath = String.format(
            "/data/contracts/%s/%s.docx",
            contract.getTenantId(),
            contract.getContractNo()
        );

        String jsonData = JsonUtils.toJson(contractData);

        ProcessBuilder pb = new ProcessBuilder(
            "python3",
            "/scripts/generate_contract.py",
            "--data", jsonData,
            "--output", outputPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new BusinessException("合同文档生成失败");
        }

        // 5. 更新合同状态
        contract.setDocumentGenerated(true);
        contract.setDocumentPath(outputPath);
        purContractMapper.updateById(contract);

        // 6. 审计日志
        auditLogService.log("CONTRACT_DOCUMENT_GENERATED", contractId);

        return outputPath;
    }

    /**
     * 转换为 PDF（用于签署）
     */
    public String convertToPDF(String docxPath) throws Exception {
        String pdfPath = docxPath.replace(".docx", ".pdf");

        ProcessBuilder pb = new ProcessBuilder(
            "libreoffice",
            "--headless",
            "--convert-to", "pdf",
            "--outdir", new File(pdfPath).getParent(),
            docxPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("PDF 转换失败");
        }

        return pdfPath;
    }
}
```

### 定时生成技术文档

```java
/**
 * 定时任务: 每周生成 API 文档
 */
@Component
public class ApiDocumentationJob {

    @XxlJob("apiDocumentationJobHandler")
    public void generateWeeklyApiDocs() {

        // 扫描所有 @RestController
        Map<String, Object> controllers = applicationContext
            .getBeansWithAnnotation(RestController.class);

        List<ApiSpec> apiSpecs = new ArrayList<>();

        for (Object controller : controllers.values()) {
            // 反射提取 API 信息
            Method[] methods = controller.getClass().getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    ApiSpec spec = extractApiSpec(method);
                    apiSpecs.add(spec);
                }
            }
        }

        // 生成 Word 文档
        String docPath = generateApiDocumentation("SCM Platform", apiSpecs);

        // 转换为 PDF
        String pdfPath = convertToPDF(docPath);

        // 上传到文档中心
        documentService.upload(pdfPath, "api-docs");
    }
}
```

## 配置

### Python 依赖

```bash
pip install python-docx lxml jinja2
pip install pandoc  # 文档转换

# PDF 转换需要 LibreOffice
apt-get install libreoffice
```

### 文档模板管理

```
/templates/
├── contract_template.docx      # 采购合同模板
├── sla_template.docx           # SLA 模板
├── adr_template.docx           # ADR 模板
├── manual_template.docx        # 操作手册模板
└── api_doc_template.docx       # API 文档模板
```

### 存储配置

```yaml
scm:
  document:
    storage:
      base-path: /data/documents
      templates: /templates
      tenant-isolation: true

    generation:
      async: true
      timeout: 180s
```

## 质量检查清单

生成文档前必须验证：

- [ ] 模板占位符全部替换
- [ ] 表格数据完整（无缺失行）
- [ ] 图片正常插入
- [ ] 中文字体正确显示
- [ ] 样式格式一致
- [ ] 文件大小合理
- [ ] 租户数据隔离正确
- [ ] PDF 转换成功（如需要）

## 常见问题

**Q: 中文字体显示为方框？**
A: 安装中文字体并在代码中指定：
```python
from docx.shared import Pt
style = doc.styles['Normal']
style.font.name = 'SimSun'  # 宋体
style._element.rPr.rFonts.set(qn('w:eastAsia'), 'SimSun')
```

**Q: 表格边框样式丢失？**
A: 使用内置表格样式或手动设置边框：
```python
table.style = 'Light Grid Accent 1'
```

**Q: 如何实现复杂的模板渲染（类似 Jinja2）？**
A: 使用 `python-docx-template` 库：
```python
from docxtpl import DocxTemplate

doc = DocxTemplate('/templates/contract.docx')
context = {'contract_no': 'C001', 'items': [...]}
doc.render(context)
doc.save('/output/contract.docx')
```

## 扩展阅读

- `scm-purchase/service` - 采购合同业务逻辑
- `docs/design/` - 技术设计文档
- `docs/guides/` - 操作手册
- Anthropic Skills: `docx-js.md` - JavaScript 实现参考