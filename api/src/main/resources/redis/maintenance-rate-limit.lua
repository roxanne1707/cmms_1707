-- One or two keys in the SAME hash slot. Two token buckets per key.
-- Validate every argument before any write: Redis scripts do not roll back errors.
if #KEYS < 1 or #KEYS > 2 or #ARGV ~= #KEYS * 4 then
    return redis.error_reply('INVALID_LIMIT_ARGUMENTS')
end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local buckets = {}
local retry = 0
for i, key in ipairs(KEYS) do
    local offset = (i - 1) * 4
    local c1,p1,c2,p2 = tonumber(ARGV[offset+1]),tonumber(ARGV[offset+2]),tonumber(ARGV[offset+3]),tonumber(ARGV[offset+4])
    if not c1 or not c2 or not p1 or not p2 or c1<1 or c2<1 or c1>100000 or c2>100000 or p1<1000 or p2<1000 or p1>86400000 or p2>86400000 then
        return redis.error_reply('INVALID_LIMIT_QUOTA')
    end
    local signature = c1..':'..p1..':'..c2..':'..p2
    local old = redis.call('HMGET',key,'signature','tokens1','tokens2','at')
    local t1,t2,at = c1,c2,now
    if old[1] == signature then
        t1,t2,at = tonumber(old[2]),tonumber(old[3]),tonumber(old[4])
        if not t1 or not t2 or not at or t1<0 or t2<0 then
            return redis.error_reply('INVALID_LIMIT_STATE')
        end
        local elapsed = math.max(0, now-at)
        t1 = math.min(c1,t1+elapsed*c1/p1)
        t2 = math.min(c2,t2+elapsed*c2/p2)
    end
    if t1 < 1 then retry=math.max(retry,math.ceil((1-t1)*p1/c1)) end
    if t2 < 1 then retry=math.max(retry,math.ceil((1-t2)*p2/c2)) end
    buckets[i] = {signature,t1,t2,math.max(now,at),math.max(p1,p2)}
end
if retry>0 then return {0, math.max(1,math.ceil(retry/1000))} end
for i,key in ipairs(KEYS) do
    local b=buckets[i]
    redis.call('HSET',key,'signature',b[1],'tokens1',b[2]-1,'tokens2',b[3]-1,'at',b[4])
    redis.call('PEXPIRE',key,b[5])
end
return {1,0}
