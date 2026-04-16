local event_id = ARGV[1]
local user_id = ARGV[2]

local rank = redis.call('zrank', 'waiting_list:'..event_id, user_id)
if rank then
    return cjson.encode({status='queued', position=rank})
end

if redis.call('zscore', 'active_users:'..event_id, user_id) then
    return cjson.encode({status='active'})
end

return cjson.encode({status='out'})