-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 可选：参数校验（防止调用方传错）
if (not voucherId) or (not userId) then
    return 3   -- 参数错误（你也可以自定义错误码）
end

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.1 判断库存是否充足
local stockStr = redis.call('get', stockKey)
if (not stockStr) then
    return 1   -- 库存不足/库存key不存在（也可以返回单独的错误码）
end

local stock = tonumber(stockStr)
if (not stock) then
    return 1   -- 库存值不是数字
end

if (stock <= 0) then
    return 1
end

-- 3.2 判断是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3.3 扣库存 + 标记下单 (NO XADD — RocketMQ handles async persistence)
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
