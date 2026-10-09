-- KEYS[1] 全局统计 Hash；KEYS[2] 当前商品统计 Hash。
-- ARGV[1] 本次是否返回 202（1 或 0）；ARGV[2] 当前 UTC 时间。
-- 先校验两个 Hash，避免损坏的计数导致写到一半才报错。
for _, key in ipairs(KEYS) do
    local kind = redis.call('TYPE', key).ok
    if kind ~= 'none' and kind ~= 'hash' then
        return redis.error_reply('Invalid metrics key type')
    end
    for _, field in ipairs({'requests', 'accepted'}) do
        local value = redis.call('HGET', key, field)
        if value and not string.match(value, '^%d+$') then
            return redis.error_reply('Invalid metrics counter')
        end
    end
end
for _, key in ipairs(KEYS) do
    -- 首次请求才记录采集起点，不会随着每次请求改变。
    redis.call('HSETNX', key, 'startedAt', ARGV[2])
    redis.call('HINCRBY', key, 'requests', 1)
    redis.call('HINCRBY', key, 'accepted', ARGV[1])
end
return 1
