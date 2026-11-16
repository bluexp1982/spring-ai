import { useRef, useState } from 'react';
import {
  App,
  Button,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Space,
  Slider,
  Switch,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  DeleteOutlined,
  InboxOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  deleteRagDocuments,
  getRagDocumentPage,
  ingestRagDocuments,
  searchRagDocuments,
  uploadRagFile,
} from '@/api/rag';
import type { RagDocument, RagIngestItem, RagSource } from '@/types/rag';

const { Text, Paragraph } = Typography;

/** 上传文件的默认切片长度，与服务端默认值保持一致 */
const DEFAULT_CHUNK_SIZE = 800;

/** 支持的纯文本扩展名，与后端校验规则一致 */
const ACCEPTED_EXTENSIONS = '.txt,.md,.markdown';

/** 取片段来源，缺失时返回空串（metadata 是后端自定义的 JSON，键不一定存在） */
function sourceOf(record: { metadata?: Record<string, unknown> }): string {
  const source = record.metadata?.source;
  return typeof source === 'string' ? source : '';
}

/** 相似度得分对应的标签颜色：越高越相关 */
function scoreColor(score?: number): string {
  if (score == null) return 'default';
  if (score >= 0.7) return 'green';
  if (score >= 0.5) return 'blue';
  return 'default';
}

/**
 * 知识库管理页面。
 *
 * 分两个页签：
 * - 文档管理：片段列表（分页 / 关键词过滤）、新增、上传文件、删除；
 * - 检索测试：只走向量检索、不调用大模型，用于调阈值与排查「答非所问」。
 */
export default function KnowledgePage() {
  // 静态 message / Modal 无法消费 ConfigProvider 的主题上下文，必须用 App.useApp()
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);

  /** 表格勾选的片段 ID，用于批量删除 */
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  /** 片段详情弹窗 */
  const [detail, setDetail] = useState<RagDocument | null>(null);

  /** 新增片段弹窗 */
  const [ingestOpen, setIngestOpen] = useState(false);
  const [ingesting, setIngesting] = useState(false);
  const [ingestForm] = Form.useForm<{ content: string; source?: string; splitByBlank: boolean }>();

  /** 上传文件弹窗 */
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadForm] = Form.useForm<{ source?: string; chunkSize: number }>();

  /** 检索测试 */
  const [testForm] = Form.useForm<{ query: string; topK: number; threshold: number }>();
  const [testing, setTesting] = useState(false);
  const [testResults, setTestResults] = useState<RagSource[] | null>(null);

  /** 删除片段（单条或批量） */
  const handleDelete = (ids: string[], tip: string) => {
    if (ids.length === 0) {
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确定要删除${tip}吗？该操作不可恢复。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteRagDocuments(ids);
          message.success(`已删除 ${ids.length} 个片段`);
          setSelectedIds([]);
          setDetail(null);
          actionRef.current?.reload();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  /** 提交新增片段 */
  const handleIngest = async () => {
    const values = await ingestForm.validateFields();
    const content = values.content.trim();
    if (!content) {
      message.warning('片段内容不能为空');
      return;
    }

    // 勾选「按空行切分」时在前端拆成多条：后端入库接口本身就是批量的，
    // 无需为粘贴场景再开一个切分接口
    const texts = values.splitByBlank
      ? content.split(/\n\s*\n/).map((part) => part.trim()).filter(Boolean)
      : [content];

    const metadata = values.source ? { source: values.source } : undefined;
    const items: RagIngestItem[] = texts.map((text) => ({ content: text, metadata }));

    setIngesting(true);
    try {
      const res = await ingestRagDocuments(items);
      message.success(`已入库 ${res.data.data.length} 个片段`);
      setIngestOpen(false);
      ingestForm.resetFields();
      actionRef.current?.reload();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '入库失败');
    } finally {
      setIngesting(false);
    }
  };

  /** 提交上传文件 */
  const handleUpload = async () => {
    if (!uploadFile) {
      message.warning('请先选择要上传的文件');
      return;
    }
    const values = await uploadForm.validateFields();

    setUploading(true);
    try {
      const res = await uploadRagFile(uploadFile, values.source, values.chunkSize);
      message.success(`已切分为 ${res.data.data.length} 个片段入库`);
      setUploadOpen(false);
      setUploadFile(null);
      uploadForm.resetFields();
      actionRef.current?.reload();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  /** 执行检索测试 */
  const handleTest = async () => {
    const values = await testForm.validateFields();
    setTesting(true);
    try {
      const res = await searchRagDocuments(values.query, values.topK, values.threshold);
      setTestResults(res.data.data);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '检索失败');
    } finally {
      setTesting(false);
    }
  };

  const columns: ProColumns<RagDocument>[] = [
    {
      // 仅用于搜索：后端按片段正文做模糊匹配，因此不展示该列
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '按片段正文模糊搜索' },
    },
    {
      title: '片段内容',
      dataIndex: 'content',
      search: false,
      ellipsis: true,
      render: (_, record) => (
        <a onClick={() => setDetail(record)} title="点击查看完整内容">
          {record.content}
        </a>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      search: false,
      width: 180,
      render: (_, record) => sourceOf(record) || <Text type="secondary">-</Text>,
    },
    {
      title: '文档ID',
      dataIndex: 'id',
      search: false,
      width: 300,
      copyable: true,
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      search: false,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => setDetail(record)}>
            查看
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={() => handleDelete([record.id], '该片段')}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  /** 页签一：文档管理 */
  const documentTab = (
    <>
      <ProTable<RagDocument>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        rowSelection={{
          selectedRowKeys: selectedIds,
          onChange: (keys) => setSelectedIds(keys as string[]),
        }}
        request={async (params) => {
          const { current, pageSize, keyword } = params;
          const res = await getRagDocumentPage({
            page: current,
            size: pageSize,
            keyword: (keyword as string) || undefined,
          });
          const page = res.data.data;
          return {
            data: page.records,
            total: page.total,
            success: true,
          };
        }}
        pagination={{
          defaultPageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total) => `共 ${total} 条`,
        }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setIngestOpen(true)}
          >
            新增片段
          </Button>,
          <Button key="upload" onClick={() => setUploadOpen(true)}>
            上传文件
          </Button>,
          <Button
            key="batchDelete"
            danger
            icon={<DeleteOutlined />}
            disabled={selectedIds.length === 0}
            onClick={() => handleDelete(selectedIds, `选中的 ${selectedIds.length} 个片段`)}
          >
            批量删除
          </Button>,
        ]}
        scroll={{ x: 1000 }}
      />

      <Modal
        title="新增知识库片段"
        open={ingestOpen}
        confirmLoading={ingesting}
        onOk={handleIngest}
        onCancel={() => setIngestOpen(false)}
        okText="入库"
        cancelText="取消"
        width={680}
        destroyOnHidden
      >
        <Form
          form={ingestForm}
          layout="vertical"
          initialValues={{ splitByBlank: false }}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="content"
            label="片段内容"
            rules={[{ required: true, message: '请输入片段内容' }]}
          >
            <Input.TextArea
              rows={10}
              placeholder="粘贴要入库的文本内容，建议一段一个语义完整的片段"
              showCount
              maxLength={8000}
            />
          </Form.Item>
          <Form.Item name="source" label="来源标识（可选）">
            <Input placeholder="例如：产品手册 / 2026 版价格表，便于检索时展示出处" />
          </Form.Item>
          <Form.Item
            name="splitByBlank"
            label="按空行自动切分"
            valuePropName="checked"
            extra="开启后会用空行把内容拆成多个片段分别入库，检索命中的粒度更细"
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="上传文本文件"
        open={uploadOpen}
        confirmLoading={uploading}
        onOk={handleUpload}
        onCancel={() => setUploadOpen(false)}
        okText="上传并入库"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form
          form={uploadForm}
          layout="vertical"
          initialValues={{ chunkSize: DEFAULT_CHUNK_SIZE }}
          style={{ marginTop: 16 }}
        >
          <Upload.Dragger
            accept={ACCEPTED_EXTENSIONS}
            maxCount={1}
            // 返回 false 阻止 antd 自动上传，改为点击确定后手动调用接口
            beforeUpload={(file) => {
              setUploadFile(file);
              return false;
            }}
            onRemove={() => setUploadFile(null)}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此处</p>
            <p className="ant-upload-hint">仅支持 .txt / .md 纯文本文件</p>
          </Upload.Dragger>

          <Divider />

          <Form.Item name="source" label="来源标识（可选）">
            <Input placeholder="留空则使用文件名" />
          </Form.Item>
          <Form.Item
            name="chunkSize"
            label="单片最大字符数"
            extra="建议 500~1000：太小会产生大量碎片，太大则检索命中不够精准"
          >
            <InputNumber min={200} max={2000} step={100} style={{ width: 160 }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="片段详情"
        open={detail !== null}
        footer={<Button type="primary" onClick={() => setDetail(null)}>关闭</Button>}
        onCancel={() => setDetail(null)}
        width={720}
        destroyOnHidden
      >
        <Paragraph style={{ marginTop: 16 }} copyable={{ text: detail?.id }}>
          <Text type="secondary">文档 ID：</Text>
          {detail?.id}
        </Paragraph>
        <Paragraph>
          <Text type="secondary">来源：</Text>
          {detail ? sourceOf(detail) || '-' : '-'}
        </Paragraph>
        <Divider style={{ margin: '12px 0' }} />
        <Paragraph
          style={{
            whiteSpace: 'pre-wrap',
            maxHeight: 400,
            overflowY: 'auto',
            background: '#fafafa',
            padding: 12,
            borderRadius: 6,
          }}
        >
          {detail?.content}
        </Paragraph>
      </Modal>
    </>
  );

  /** 页签二：检索测试 */
  const searchTab = (
    <>
      <Form
        form={testForm}
        layout="inline"
        initialValues={{ topK: 4, threshold: 0.5 }}
        style={{ rowGap: 12 }}
      >
        <Form.Item
          name="query"
          label="查询内容"
          rules={[{ required: true, message: '请输入查询内容' }]}
        >
          <Input
            placeholder="输入要检索的问题"
            style={{ width: 320 }}
            onPressEnter={handleTest}
            allowClear
          />
        </Form.Item>
        <Form.Item name="topK" label="召回条数">
          <InputNumber min={1} max={20} />
        </Form.Item>
        <Form.Item name="threshold" label="相似度阈值">
          <Slider min={0} max={1} step={0.05} style={{ width: 180 }} />
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            icon={<SearchOutlined />}
            loading={testing}
            onClick={handleTest}
          >
            检索
          </Button>
        </Form.Item>
      </Form>

      <Divider style={{ margin: '8px 0 16px' }} />

      {testResults === null ? (
        <Empty description="输入内容后点击「检索」，可单独观察召回结果与相似度得分" />
      ) : testResults.length === 0 ? (
        <Empty description="没有命中任何片段，可尝试调低相似度阈值或更换问法" />
      ) : (
        <List
          size="small"
          bordered
          dataSource={testResults}
          header={<Text type="secondary">命中 {testResults.length} 个片段（按相似度降序）</Text>}
          renderItem={(item) => (
            <List.Item>
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Space size={8} wrap>
                  <Tag color={scoreColor(item.score)}>
                    score {item.score != null ? item.score.toFixed(4) : '-'}
                  </Tag>
                  {sourceOf(item) && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      来源：{sourceOf(item)}
                    </Text>
                  )}
                </Space>
                <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {item.content}
                </Paragraph>
              </Space>
            </List.Item>
          )}
        />
      )}
    </>
  );

  return (
    <Tabs
      defaultActiveKey="documents"
      items={[
        { key: 'documents', label: '文档管理', children: documentTab },
        { key: 'search', label: '检索测试', children: searchTab },
      ]}
    />
  );
}
