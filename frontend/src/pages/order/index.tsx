import { useRef, useState } from 'react';
import { App, Button, Modal, Tag, Space, Descriptions } from 'antd';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { getOrderPage, deleteOrder } from '@/api/order';
import { getPlanOptions } from '@/api/plan';
import type { SaasSubscriptionOrder, OrderStatus, OrderType } from '@/types/order';
import { ORDER_STATUS_MAP, ORDER_TYPE_MAP } from '@/types/order';

export default function OrderPage() {
  // 使用 App.useApp() 获取实例，静态 message / Modal 无法消费 ConfigProvider 的主题上下文
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [viewOpen, setViewOpen] = useState(false);
  const [viewingOrder, setViewingOrder] = useState<SaasSubscriptionOrder | null>(null);

  const columns: ProColumns<SaasSubscriptionOrder>[] = [
    {
      title: '订单ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      search: false,
    },
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 180,
      copyable: true,
    },
    {
      title: '用户',
      dataIndex: 'userName',
      key: 'userName',
      width: 100,
      render: (_, record) => record.userName || `#${record.userId}`,
    },
    {
      title: '套餐',
      dataIndex: 'planName',
      key: 'planName',
      width: 100,
      search: false,
      render: (_, record) => record.planName || `#${record.planId}`,
    },
    {
      // 仅用于搜索：绑定 planId，用户直接选择套餐即可
      title: '套餐',
      dataIndex: 'planId',
      key: 'planId',
      hideInTable: true,
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        placeholder: '请选择套餐',
      },
      request: getPlanOptions,
    },
    {
      title: '订单类型',
      dataIndex: 'orderType',
      key: 'orderType',
      width: 90,
      valueEnum: ORDER_TYPE_MAP,
    },
    {
      title: '订单金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 100,
      search: false,
      render: (_, record) => `¥${record.amount?.toFixed(2)}`,
    },
    {
      title: '实付金额',
      dataIndex: 'finalAmount',
      key: 'finalAmount',
      width: 100,
      search: false,
      render: (_, record) => `¥${record.finalAmount?.toFixed(2)}`,
    },
    {
      title: '支付方式',
      dataIndex: 'paymentMethod',
      key: 'paymentMethod',
      width: 90,
      search: false,
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (_, record) => {
        const config = ORDER_STATUS_MAP[record.status as OrderStatus];
        return config ? <Tag color={config.color}>{config.label}</Tag> : record.status;
      },
      valueEnum: Object.fromEntries(
        Object.entries(ORDER_STATUS_MAP).map(([key, val]) => [key, val.label])
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      valueType: 'dateRange',
      render: (_, record) => {
        if (!record.createdAt) return '-';
        const d = new Date(record.createdAt);
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
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

  const handleView = (record: SaasSubscriptionOrder) => {
    setViewingOrder(record);
    setViewOpen(true);
  };

  const handleDelete = (record: SaasSubscriptionOrder) => {
    modal.confirm({
      title: '确认删除',
      content: `确定要删除订单「${record.orderNo}」吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteOrder(record.id!);
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
      <ProTable<SaasSubscriptionOrder>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          // 处理创建时间范围查询
          let startDate: string | undefined;
          let endDate: string | undefined;
          if (Array.isArray(rest.createdAt) && rest.createdAt.length === 2) {
            startDate = rest.createdAt[0] ? `${rest.createdAt[0]} 00:00:00` : undefined;
            endDate = rest.createdAt[1] ? `${rest.createdAt[1]} 23:59:59` : undefined;
          }
          const res = await getOrderPage({
            page: current,
            size: pageSize,
            orderNo: rest.orderNo as string,
            status: rest.status as OrderStatus,
            orderType: rest.orderType as OrderType,
            // 「用户」列绑定的是展示字段，后端按名称模糊匹配
            userName: (rest.userName as string) || undefined,
            // 空值置为 undefined，避免空字符串传给后端 Long 类型导致 400
            userId: rest.userId ? Number(rest.userId) : undefined,
            planId: rest.planId ? Number(rest.planId) : undefined,
            startDate,
            endDate,
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
        scroll={{ x: 1280 }}
      />

      <Modal
        title="订单详情"
        open={viewOpen}
        footer={<Button type="primary" onClick={() => setViewOpen(false)}>关闭</Button>}
        onCancel={() => {
          setViewOpen(false);
          setViewingOrder(null);
        }}
        width={640}
        destroyOnHidden
      >
        <Descriptions column={2} bordered size="small" style={{ marginTop: 16 }}>
          <Descriptions.Item label="订单编号" span={2}>{viewingOrder?.orderNo}</Descriptions.Item>
          <Descriptions.Item label="用户">{viewingOrder?.userName || (viewingOrder?.userId ? `#${viewingOrder.userId}` : '-')}</Descriptions.Item>
          <Descriptions.Item label="套餐">{viewingOrder?.planName || (viewingOrder?.planId ? `#${viewingOrder.planId}` : '-')}</Descriptions.Item>
          <Descriptions.Item label="订阅ID">{viewingOrder?.subscriptionId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="优惠券ID">{viewingOrder?.couponId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="订单类型">{viewingOrder?.orderType ? ORDER_TYPE_MAP[viewingOrder.orderType as OrderType] : '-'}</Descriptions.Item>
          <Descriptions.Item label="订单状态">
            {viewingOrder?.status ? (() => {
              const cfg = ORDER_STATUS_MAP[viewingOrder.status as OrderStatus];
              return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : viewingOrder.status;
            })() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="订单金额">{viewingOrder?.amount != null ? `¥${viewingOrder.amount.toFixed(2)}` : '-'}</Descriptions.Item>
          <Descriptions.Item label="实付金额">{viewingOrder?.finalAmount != null ? `¥${viewingOrder.finalAmount.toFixed(2)}` : '-'}</Descriptions.Item>
          <Descriptions.Item label="优惠金额">{viewingOrder?.discountAmount != null ? `¥${viewingOrder.discountAmount.toFixed(2)}` : '-'}</Descriptions.Item>
          <Descriptions.Item label="币种">{viewingOrder?.currency ?? '-'}</Descriptions.Item>
          {/* span=2 的项必须从行首开始，否则该行 span 之和会超过 column=2 而告警 */}
          <Descriptions.Item label="交易流水号" span={2}>{viewingOrder?.transactionId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="支付方式">{viewingOrder?.paymentMethod ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="支付时间">{viewingOrder?.paidAt ? new Date(viewingOrder.paidAt).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="计费周期开始">{viewingOrder?.periodStart ? new Date(viewingOrder.periodStart).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="计费周期结束">{viewingOrder?.periodEnd ? new Date(viewingOrder.periodEnd).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间" span={2}>{viewingOrder?.createdAt ? new Date(viewingOrder.createdAt).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>
      </Modal>
    </>
  );
}