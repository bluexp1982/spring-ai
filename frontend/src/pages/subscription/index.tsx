import { useRef, useState } from 'react';
import { App, Button, Modal, Tag, Space, Descriptions } from 'antd';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { getSubscriptionPage, deleteSubscription } from '@/api/subscription';
import { getPlanOptions } from '@/api/plan';
import type { SaasSubscription, SubscriptionStatus } from '@/types/subscription';
import { SUBSCRIPTION_STATUS_MAP } from '@/types/subscription';

function formatDt(dt?: string): string {
  if (!dt) return '-';
  const d = new Date(dt);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function SubscriptionPage() {
  // 使用 App.useApp() 获取实例，静态 message / Modal 无法消费 ConfigProvider 的主题上下文
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [viewOpen, setViewOpen] = useState(false);
  const [viewingSub, setViewingSub] = useState<SaasSubscription | null>(null);

  const columns: ProColumns<SaasSubscription>[] = [
    {
      title: '订阅ID',
      dataIndex: 'id',
      key: 'id',
      width: 50,
      search: false,
    },
    {
      title: '用户',
      dataIndex: 'userName',
      key: 'userName',
      width: 50,
      render: (_, record) => record.userName || `#${record.userId}`,
    },
    {
      title: '套餐',
      dataIndex: 'planName',
      key: 'planName',
      width: 80,
      search: false,
      render: (_, record) => record.planName || `#${record.planId}`,
    },
    {
      // 仅用于搜索：绑定 planId，用户直接选择套餐即可
      title: '套餐',
      dataIndex: 'planId',
      key: 'planId',
      width: 50,
      hideInTable: true,
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        placeholder: '请选择套餐',
      },
      request: getPlanOptions,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 50,
      render: (_, record) => {
        const config = SUBSCRIPTION_STATUS_MAP[record.status as SubscriptionStatus];
        return config ? <Tag color={config.color}>{config.label}</Tag> : record.status;
      },
      valueEnum: Object.fromEntries(
        Object.entries(SUBSCRIPTION_STATUS_MAP).map(([key, val]) => [key, val.label])
      ),
    },
    {
      title: '自动续费',
      dataIndex: 'autoRenew',
      key: 'autoRenew',
      width: 50,
      search: false,
      render: (_, record) =>
        record.autoRenew ? <Tag color="blue">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '订阅开始',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 80,
      search: false,
      render: (_, record) => formatDt(record.startedAt),
    },
    {
      title: '到期时间',
      dataIndex: 'expiredAt',
      key: 'expiredAt',
      width: 80,
      search: false,
      render: (_, record) => formatDt(record.expiredAt),
    },
    {
      title: '当前计费周期',
      key: 'currentPeriod',
      width: 80,
      search: false,
      render: (_, record) => {
        if (!record.currentPeriodStart && !record.currentPeriodEnd) return '-';
        // 起止时间改为上下两行展示，避免单行过长挤压相邻列
        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.5 }}>
            <span>{formatDt(record.currentPeriodStart)}</span>
            <span>{formatDt(record.currentPeriodEnd)}</span>
          </div>
        );
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 80,
      search: false,
      render: (_, record) => formatDt(record.createdAt),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right',
      search: false,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const handleView = (record: SaasSubscription) => {
    setViewingSub(record);
    setViewOpen(true);
  };

  const handleDelete = (record: SaasSubscription) => {
    modal.confirm({
      title: '确认删除',
      content: `确定要删除订阅 #${record.id} 吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSubscription(record.id!);
          message.success('删除成功');
          actionRef.current?.reload();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  return (
    <>
      <ProTable<SaasSubscription>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const res = await getSubscriptionPage({
            page: current,
            size: pageSize,
            status: rest.status as SubscriptionStatus,
            // 「用户」列绑定的是展示字段，后端按名称模糊匹配
            userName: (rest.userName as string) || undefined,
            // 空值置为 undefined，避免空字符串传给后端 Long 类型导致 400
            userId: rest.userId ? Number(rest.userId) : undefined,
            planId: rest.planId ? Number(rest.planId) : undefined,
          });
          return {
            data: res.data.data.records,
            total: res.data.data.total,
            success: true,
          };
        }}
        pagination={{
          defaultPageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total) => `共 ${total} 条`,
        }}
        search={{
          labelWidth: 'auto',
        }}
        toolBarRender={() => []}
        scroll={{ x: 1400 }}
      />

      <Modal
        title="订阅详情"
        open={viewOpen}
        footer={<Button type="primary" onClick={() => setViewOpen(false)}>关闭</Button>}
        onCancel={() => {
          setViewOpen(false);
          setViewingSub(null);
        }}
        width={720}
        destroyOnHidden
      >
        <Descriptions column={2} bordered size="small" style={{ marginTop: 16 }}>
          <Descriptions.Item label="订阅ID">{viewingSub?.id}</Descriptions.Item>
          <Descriptions.Item label="用户">{viewingSub?.userName || (viewingSub?.userId ? `#${viewingSub.userId}` : '-')}</Descriptions.Item>
          <Descriptions.Item label="套餐">{viewingSub?.planName || (viewingSub?.planId ? `#${viewingSub.planId}` : '-')}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {viewingSub?.status ? (() => {
              const cfg = SUBSCRIPTION_STATUS_MAP[viewingSub.status as SubscriptionStatus];
              return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : viewingSub.status;
            })() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="自动续费">
            {viewingSub?.autoRenew != null ? (viewingSub.autoRenew ? <Tag color="blue">是</Tag> : <Tag>否</Tag>) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="订阅开始时间">{formatDt(viewingSub?.startedAt)}</Descriptions.Item>
          <Descriptions.Item label="到期时间">{formatDt(viewingSub?.expiredAt)}</Descriptions.Item>
          <Descriptions.Item label="试用结束时间">{formatDt(viewingSub?.trialEndAt)}</Descriptions.Item>
          <Descriptions.Item label="取消时间">{formatDt(viewingSub?.cancelledAt)}</Descriptions.Item>
          <Descriptions.Item label="暂停时间">{formatDt(viewingSub?.pausedAt)}</Descriptions.Item>
          <Descriptions.Item label="暂停恢复时间">{formatDt(viewingSub?.pausedUntil)}</Descriptions.Item>
          <Descriptions.Item label="当前周期开始">{formatDt(viewingSub?.currentPeriodStart)}</Descriptions.Item>
          {/* 前置单列字段为奇数个，此处让该项独占一行，避免后续元数据(span=2)落在半行处 */}
          <Descriptions.Item label="当前周期结束" span={2}>{formatDt(viewingSub?.currentPeriodEnd)}</Descriptions.Item>
          <Descriptions.Item label="元数据" span={2}>
            {viewingSub?.metadata ? JSON.stringify(viewingSub.metadata, null, 2) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间" span={2}>{formatDt(viewingSub?.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间" span={2}>{formatDt(viewingSub?.updatedAt)}</Descriptions.Item>
        </Descriptions>
      </Modal>
    </>
  );
}