local event_id = ARGV[1]
local user_id = ARGV[2]

local existing = redis.call('zrank', 'waiting_list:'..event_id, user_id)
if existing then
    return cjson.encode({
        alreadyInQueue = true,
        position = tonumber(existing)
    })
end

local t = redis.call("time")
local score = tonumber(t[1]) * 1000000 + tonumber(t[2])
redis.call('zadd', 'waiting_list:'..event_id, score, user_id)
local sequence = redis.call('zrank', 'waiting_list:'..event_id, user_id)

return cjson.encode({
    alreadyInQueue = false,
    position = tonumber(sequence)
})