CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES delivery_requests(id),
    helper_id BIGINT NOT NULL REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (request_id, helper_id)
);
CREATE INDEX ix_conversations_helper ON conversations(helper_id, id DESC);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    sender_id BIGINT NOT NULL REFERENCES users(id),
    client_message_id UUID NOT NULL,
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, sender_id, client_message_id)
);
CREATE INDEX ix_chat_messages_history ON chat_messages(conversation_id, id DESC);
