-- PostgreSQL. Run the WHOLE file in one connection (UTF-8).
-- Default: send only to ynw68xwmjc. For ALL eligible users, change the one option below
-- from 'ynw68xwmjc'::text to NULL::text, then run the whole file again.
-- This file COMMITs. To rehearse without saving, replace the final COMMIT with ROLLBACK.
-- Does not trigger Java events, push notifications, notification rows or WebSocket updates.
-- A prior message in either direction, including soft-deleted messages, excludes a pair.
-- Group/booking/assistant messages do not count as a prior personal 1:1 conversation.
-- No migration, extension, permanent table or application configuration is changed.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TEMP TABLE dev_welcome_options ON COMMIT DROP AS
SELECT 'quyennv'::text AS sender_username,
       'ynw68xwmjc'::text AS target_username; -- NULL::text = ALL eligible users

CREATE TEMP TABLE dev_welcome_results (
    username text,
    outcome text NOT NULL,
    conversation_id uuid,
    message_id uuid
) ON COMMIT DROP;

-- Plain reads remain available. Chat writes / SELECT FOR UPDATE wait until COMMIT.
-- The app has no unique constraint on a DIRECT pair; these short-lived locks prevent
-- a concurrent start/send from creating another thread or racing sequence allocation.
-- If chat is busy, lock_timeout aborts the transaction rather than waiting indefinitely.
LOCK TABLE marketplace_conversations,
           marketplace_conversation_members,
           marketplace_messages IN EXCLUSIVE MODE;

DO $welcome$
DECLARE
    v_sender_username text;
    v_target_username text;
    v_sender_id uuid;
    v_recipient record;
    v_conversation_id uuid;
    v_message_id uuid;
    v_sequence bigint;
    v_now timestamp;
    v_content constant text := $message$Hi b
Tui là dev của app Ondetour. Cảm ơn b đã tải và sử dụng app nhé, nếu có vấn đề gì khi sử dụng app hay muốn cải tiến gì thì b hãy nhắn tui nha 🥰$message$;
    v_campaign constant text := 'dev-welcome:2026-11-06';
BEGIN
    SELECT sender_username, target_username
    INTO STRICT v_sender_username, v_target_username
    FROM dev_welcome_options;

    -- INTO STRICT fails before writing if the sender is missing or ambiguous.
    SELECT id INTO STRICT v_sender_id
    FROM users
    WHERE lower(username) = lower(v_sender_username)
      AND deleted_at IS NULL
      AND account_status = 'ACTIVE'
      AND account_type = 'HUMAN';

    IF v_target_username IS NOT NULL THEN
        IF lower(v_target_username) = lower(v_sender_username) THEN
            RAISE EXCEPTION 'Sender and recipient must be different';
        END IF;
        IF (SELECT count(*) FROM users
            WHERE lower(username) = lower(v_target_username)
              AND deleted_at IS NULL AND account_status = 'ACTIVE'
              AND account_type = 'HUMAN') <> 1 THEN
            RAISE EXCEPTION 'Recipient % is missing, ambiguous or not an active human account',
                v_target_username;
        END IF;
    END IF;

    FOR v_recipient IN
        SELECT id, username FROM users
        WHERE id <> v_sender_id
          AND deleted_at IS NULL
          AND account_status = 'ACTIVE'
          AND account_type = 'HUMAN'
          AND (v_target_username IS NULL OR lower(username) = lower(v_target_username))
        ORDER BY id
    LOOP
        IF EXISTS (
            SELECT 1 FROM user_blocks
            WHERE (blocker_id = v_sender_id AND blocked_id = v_recipient.id)
               OR (blocker_id = v_recipient.id AND blocked_id = v_sender_id)
        ) THEN
            INSERT INTO dev_welcome_results VALUES
                (v_recipient.username, 'SKIPPED_BLOCKED', NULL, NULL);
            CONTINUE;
        END IF;

        -- Check ALL historical 1:1 threads, not just the first/visible one.
        -- Include departed members and deleted messages: they still prove prior contact.
        IF EXISTS (
            SELECT 1
            FROM marketplace_conversations c
            JOIN marketplace_conversation_members s
              ON s.conversation_id = c.id AND s.user_id = v_sender_id
            JOIN marketplace_conversation_members r
              ON r.conversation_id = c.id AND r.user_id = v_recipient.id
            WHERE c.conversation_type = 'DIRECT' AND c.organization_id IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM marketplace_conversation_members other_member
                  WHERE other_member.conversation_id = c.id
                    AND other_member.user_id NOT IN (v_sender_id, v_recipient.id)
              )
              AND EXISTS (SELECT 1 FROM marketplace_messages m WHERE m.conversation_id = c.id)
        ) THEN
            INSERT INTO dev_welcome_results VALUES
                (v_recipient.username, 'SKIPPED_PRIOR_MESSAGES', NULL, NULL);
            CONTINUE;
        END IF;

        -- Do not reopen a closed thread or re-add somebody who already left it.
        IF EXISTS (
            SELECT 1
            FROM marketplace_conversations c
            JOIN marketplace_conversation_members s
              ON s.conversation_id = c.id AND s.user_id = v_sender_id
            JOIN marketplace_conversation_members r
              ON r.conversation_id = c.id AND r.user_id = v_recipient.id
            WHERE c.conversation_type = 'DIRECT' AND c.organization_id IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM marketplace_conversation_members other_member
                  WHERE other_member.conversation_id = c.id
                    AND other_member.user_id NOT IN (v_sender_id, v_recipient.id)
              )
              AND (c.status NOT IN ('OPEN', 'PENDING', 'RESOLVED')
                   OR s.left_at IS NOT NULL OR r.left_at IS NOT NULL)
        ) THEN
            INSERT INTO dev_welcome_results VALUES
                (v_recipient.username, 'SKIPPED_INACTIVE_THREAD', NULL, NULL);
            CONTINUE;
        END IF;

        -- Same active-member lookup as the app: reuse an empty DIRECT thread.
        SELECT c.id INTO v_conversation_id
        FROM marketplace_conversations c
        JOIN marketplace_conversation_members s
          ON s.conversation_id = c.id AND s.user_id = v_sender_id AND s.left_at IS NULL
        JOIN marketplace_conversation_members r
          ON r.conversation_id = c.id AND r.user_id = v_recipient.id AND r.left_at IS NULL
        WHERE c.conversation_type = 'DIRECT' AND c.organization_id IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM marketplace_conversation_members other_member
              WHERE other_member.conversation_id = c.id AND other_member.left_at IS NULL
                AND other_member.user_id NOT IN (v_sender_id, v_recipient.id)
          )
        ORDER BY c.created_at, c.id
        LIMIT 1;

        IF v_conversation_id IS NOT NULL THEN
            -- A former group with two active members may also match the app's lookup.
            -- Never send into its history or bypass its status, even if it was not a 1:1.
            IF EXISTS (SELECT 1 FROM marketplace_messages WHERE conversation_id = v_conversation_id)
               OR EXISTS (SELECT 1 FROM marketplace_conversations
                          WHERE id = v_conversation_id AND status NOT IN ('OPEN', 'PENDING', 'RESOLVED')) THEN
                INSERT INTO dev_welcome_results VALUES
                    (v_recipient.username, 'SKIPPED_EXISTING_THREAD', v_conversation_id, NULL);
                CONTINUE;
            END IF;
        END IF;

        v_now := clock_timestamp()::timestamp;
        IF v_conversation_id IS NULL THEN
            v_conversation_id := gen_random_uuid();
            INSERT INTO marketplace_conversations
                (id, conversation_type, status, created_at, updated_at)
            VALUES (v_conversation_id, 'DIRECT', 'OPEN', v_now, v_now);

            INSERT INTO marketplace_conversation_members
                (conversation_id, user_id, member_role, joined_at)
            VALUES (v_conversation_id, v_sender_id, 'CREATOR', v_now),
                   (v_conversation_id, v_recipient.id, 'PARTICIPANT', v_now);
        END IF;

        SELECT coalesce(max(sequence_no), 0) + 1 INTO v_sequence
        FROM marketplace_messages WHERE conversation_id = v_conversation_id;

        v_message_id := gen_random_uuid();
        INSERT INTO marketplace_messages
            (id, conversation_id, sender_user_id, client_message_id,
             message_type, content, attachments, sequence_no, created_at)
        VALUES
            (v_message_id, v_conversation_id, v_sender_id, v_campaign,
             'TEXT', v_content, '[]'::jsonb, v_sequence, v_now);

        UPDATE marketplace_conversations
        SET last_message_at = v_now, updated_at = v_now
        WHERE id = v_conversation_id;

        INSERT INTO dev_welcome_results VALUES
            (v_recipient.username, 'SENT', v_conversation_id, v_message_id);
    END LOOP;
END
$welcome$;

SELECT outcome, count(*) AS user_count
FROM dev_welcome_results GROUP BY outcome ORDER BY outcome;

SELECT r.username, r.outcome, r.conversation_id, r.message_id, m.content
FROM dev_welcome_results r
LEFT JOIN marketplace_messages m ON m.id = r.message_id
ORDER BY r.outcome, r.username;

COMMIT;
