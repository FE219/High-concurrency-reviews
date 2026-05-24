-- CAS Set：仅当 key 的当前值等于 expectedValue 时才写入 newValue
-- 返回值：1=写入成功, 0=跳过（值已被并发修改）
local key = KEYS[1]
local expectedValue = ARGV[1]
local newValue = ARGV[2]

local current = redis.call('get', key)
if current == expectedValue then
    redis.call('set', key, newValue)
    return 1
end
return 0
