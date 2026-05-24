-- 为订单表添加乐观锁版本号字段
-- 用于未支付订单自动关闭与支付回调的并发控制
ALTER TABLE tb_voucher_order ADD COLUMN version INT DEFAULT 0 NOT NULL;
