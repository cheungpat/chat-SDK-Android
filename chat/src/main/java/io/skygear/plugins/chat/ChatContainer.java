package io.skygear.plugins.chat;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.skygear.skygear.Asset;
import io.skygear.skygear.AssetPostRequest;
import io.skygear.skygear.AuthenticationException;
import io.skygear.skygear.Container;
import io.skygear.skygear.Database;
import io.skygear.skygear.LambdaResponseHandler;
import io.skygear.skygear.Pubsub;
import io.skygear.skygear.Query;
import io.skygear.skygear.Record;
import io.skygear.skygear.RecordQueryResponseHandler;
import io.skygear.skygear.RecordSaveResponseHandler;
import io.skygear.skygear.Reference;

public final class ChatContainer {
    private static final int GET_MESSAGES_DEFAULT_LIMIT = 50; // default value
    private static final String TAG = "SkygearChatContainer";

    private static ChatContainer sharedInstance;

    private final Container skygear;
    private final Map<String, Subscription> messageSubscription = new HashMap<>();

    /* --- Constructor --- */

    public static ChatContainer getInstance(@NonNull final Container container) {
        if (sharedInstance == null) {
            sharedInstance = new ChatContainer(container);
        }

        return sharedInstance;
    }

    private ChatContainer(final Container container) {
        if (container != null) {
            this.skygear = container;
        } else {
            throw new NullPointerException("Container can't be null");
        }
    }

    /* --- Conversation --- */

    public void createConversation(@NonNull final Set<String> participantIds,
                                   @Nullable final String title,
                                   @Nullable final Map<String, Object> metadata,
                                   @Nullable final Map<Conversation.OptionKey, Object> options,
                                   @Nullable final SaveCallback<Conversation> callback) {
        Record record = Conversation.newRecord(participantIds, title, metadata, options);
        skygear.getPublicDatabase().save(record, new SaveResponseAdapter<Conversation>(callback) {
            @Nullable
            @Override
            public Conversation convert(Record record) {
                return new Conversation(record);
            }
        });
    }

    public void createDirectConversation(@NonNull final String participantId,
                                         @Nullable final String title,
                                         @Nullable final Map<String, Object> metadata,
                                         @Nullable final SaveCallback<Conversation> callback) {
        Set<String> participantIds = new HashSet<>();
        participantIds.add(this.skygear.getCurrentUser().getId());
        participantIds.add(participantId);

        Map<Conversation.OptionKey, Object> options = new HashMap<>();
        options.put(Conversation.OptionKey.DISTINCT_BY_PARTICIPANTS, true);

        Record record = Conversation.newRecord(participantIds, title, metadata, options);
        this.skygear.getPublicDatabase().save(record, new SaveResponseAdapter<Conversation>(callback) {
            @Nullable
            @Override
            public Conversation convert(Record record) {
                return new Conversation(record);
            }
        });
    }

    public void getConversations(@Nullable final GetCallback<List<Conversation>> callback) {
        String userId = this.skygear.getCurrentUser().getId();
        this.skygear.getPublicDatabase().query(UserConversation.buildQuery(userId),
                new QueryResponseAdapter<List<Conversation>>(callback) {
                    @Override
                    public List<Conversation> convert(Record[] records) {
                        List<Conversation> conversations = new ArrayList<>(records.length);

                        for (Record record : records) {
                            conversations.add(UserConversation.getConversation(record));
                        }

                        return conversations;
                    }
                });
    }

    public void getConversation(@NonNull final String conversationId,
                                @Nullable final GetCallback<Conversation> callback) {
        String userId = this.skygear.getCurrentUser().getId();
        this.skygear.getPublicDatabase().query(UserConversation.buildQuery(conversationId, userId),
                new QueryResponseAdapter<Conversation>(callback) {
                    @Override
                    public Conversation convert(Record[] records) {
                        return UserConversation.getConversation(records[0]);
                    }
                });
    }

    public void setConversationTitle(@NonNull final Conversation conversation,
                                     @NonNull final String title,
                                     @Nullable final SaveCallback<Conversation> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put(Conversation.TITLE_KEY, title);

        this.updateConversation(conversation, map, callback);
    }

    public void setConversationAdminIds(@NonNull final Conversation conversation,
                                        @NonNull final Set<String> adminIds,
                                        @Nullable final SaveCallback<Conversation> callback) {
        Map<String, Object> map = new HashMap<>();
        String[] ids = new String[adminIds.size()];
        adminIds.toArray(ids);
        map.put(Conversation.ADMIN_IDS_KEY, ids);

        this.updateConversation(conversation, map, callback);
    }

    public void addConversationAdminId(@NonNull final Conversation conversation,
                                       @NonNull final String adminId,
                                       @Nullable final SaveCallback<Conversation> callback) {
        Set<String> adminIds = conversation.getAdminIds();
        if (adminIds == null) {
            adminIds = new HashSet<>();
        }
        adminIds.add(adminId);

        this.setConversationAdminIds(conversation, adminIds, callback);
    }

    public void removeConversationAdminId(@NonNull final Conversation conversation,
                                          @NonNull final String adminId,
                                          @Nullable final SaveCallback<Conversation> callback) {
        Set<String> adminIds = conversation.getAdminIds();
        if (adminIds == null) {
            adminIds = new HashSet<>();
        }
        adminIds.remove(adminId);

        this.setConversationAdminIds(conversation, adminIds, callback);
    }

    public void setConversationParticipantsIds(@NonNull final Conversation conversation,
                                               @NonNull final Set<String> participantIds,
                                               @Nullable final SaveCallback<Conversation> callback) {
        Map<String, Object> map = new HashMap<>();
        String[] ids = new String[participantIds.size()];
        participantIds.toArray(ids);
        map.put(Conversation.PARTICIPANT_IDS_KEY, ids);

        this.updateConversation(conversation, map, callback);
    }

    public void addConversationParticipantId(@NonNull final Conversation conversation,
                                             @NonNull final String participantId,
                                             @Nullable final SaveCallback<Conversation> callback) {
        Set<String> participantIds = conversation.getParticipantIds();
        if (participantIds == null) {
            participantIds = new HashSet<>();
        }
        participantIds.add(participantId);

        this.setConversationParticipantsIds(conversation, participantIds, callback);
    }

    public void removeConversationParticipantId(@NonNull final Conversation conversation,
                                                @NonNull final String participantId,
                                                @Nullable final SaveCallback<Conversation> callback) {
        Set<String> participantIds = conversation.getParticipantIds();
        if (participantIds == null) {
            participantIds = new HashSet<>();
        }
        participantIds.remove(participantId);

        this.setConversationParticipantsIds(conversation, participantIds, callback);
    }

    public void setConversationDistinctByParticipants(@NonNull final Conversation conversation,
                                                      @NonNull final boolean isDistinctByParticipants,
                                                      @Nullable final SaveCallback<Conversation> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put(Conversation.DISTINCT_BY_PARTICIPANTS_KEY, isDistinctByParticipants);

        this.updateConversation(conversation, map, callback);
    }

    public void setConversationMetadata(@NonNull final Conversation conversation,
                                        @NonNull final Map<String, Object> metadata,
                                        @Nullable final SaveCallback<Conversation> callback) {
        JSONObject metadataJSON = new JSONObject(metadata);
        Map<String, Object> map = new HashMap<>();
        map.put(Conversation.METADATA_KEY, metadataJSON);

        this.updateConversation(conversation, map, callback);
    }

    public void deleteConversation(@NonNull final Conversation conversation,
                                   @Nullable final DeleteOneCallback callback) {
        final Database publicDB = this.skygear.getPublicDatabase();
        String userId = this.skygear.getCurrentUser().getId();

        GetCallback<Record> getCallback = new GetCallback<Record>() {
            @Override
            public void onSucc(@Nullable Record record) {
                if (record != null) {
                    publicDB.delete(record, new DeleteResponseAdapter(callback));
                }
            }

            @Override
            public void onFail(@Nullable String failReason) {
                if (callback != null) {
                    callback.onFail(failReason);
                }
            }
        };

        publicDB.query(UserConversation.buildQuery(conversation.getId(), userId),
                new QueryResponseAdapter<Record>(getCallback) {
                    @Override
                    public Record convert(Record[] records) {
                        return UserConversation.getConversationRecord(records[0]);
                    }
                });
    }

    public void markConversationLastReadMessage(@NonNull final Conversation conversation,
                                                @NonNull final String messageId) {
        final Database publicDB = this.skygear.getPublicDatabase();
        String userId = this.skygear.getCurrentUser().getId();

        GetCallback<Record> getCallback = new GetCallback<Record>() {
            @Override
            public void onSucc(@Nullable Record record) {
                if (record != null) {
                    record.set(UserConversation.LAST_READ_MESSAGE_KEY,
                            Message.newReference(messageId));
                    publicDB.save(record, null);
                }
            }

            @Override
            public void onFail(@Nullable String failReason) {

            }
        };

        publicDB.query(UserConversation.buildQuery(conversation.getId(), userId),
                new QueryResponseAdapter<Record>(getCallback) {
                    @Override
                    public Record convert(Record[] records) {
                        return records[0];
                    }
                });
    }

    public void updateConversation(final Conversation conversation,
                                   final Map<String, Object> updates,
                                   final SaveCallback<Conversation> callback) {
        final Database publicDB = this.skygear.getPublicDatabase();
        String userId = this.skygear.getCurrentUser().getId();

        GetCallback<Record> getCallback = new GetCallback<Record>() {
            @Override
            public void onSucc(@Nullable Record record) {
                if (record != null) {
                    for (Map.Entry<String, Object> entry : updates.entrySet()) {
                        record.set(entry.getKey(), entry.getValue());
                    }
                    publicDB.save(record, new SaveResponseAdapter<Conversation>(callback) {
                        @Override
                        public Conversation convert(Record record) {
                            return new Conversation(record);
                        }
                    });
                }
            }

            @Override
            public void onFail(@Nullable String failReason) {
                if (callback != null) {
                    callback.onFail(failReason);
                }
            }
        };

        publicDB.query(UserConversation.buildQuery(conversation.getId(), userId),
                new QueryResponseAdapter<Record>(getCallback) {
                    @Override
                    public Record convert(Record[] records) {
                        return UserConversation.getConversationRecord(records[0]);
                    }
                });
    }

    /* --- Message --- */

    public void getMessages(@NonNull final Conversation conversation,
                            final int limit,
                            @Nullable final Date before,
                            @Nullable final GetCallback<List<Message>> callback) {
        int limitCount = limit;
        String beforeTimeISO8601 = DateUtils.toISO8601(before != null ? before : new Date());

        if (limitCount <= 0) {
            limitCount = GET_MESSAGES_DEFAULT_LIMIT;
        }

        Object[] args = new Object[]{conversation.getId(), limitCount, beforeTimeISO8601};
        this.skygear.callLambdaFunction("chat:get_messages", args, new LambdaResponseHandler() {
            @Override
            public void onLambdaSuccess(JSONObject result) {
                List<Message> messages = null;
                JSONArray results = result.optJSONArray("results");

                if (results != null) {
                    messages = new ArrayList<>(results.length());

                    for (int i = 0; i < results.length(); i++) {
                        try {
                            JSONObject object = results.getJSONObject(i);
                            Record record = Record.fromJson(object);
                            Message message = new Message(record);
                            messages.add(message);
                        } catch (JSONException e) {
                            Log.e(TAG, "Fail to get message: " + e.getMessage());
                        }
                    }
                }
                if (callback != null) {
                    callback.onSucc(messages);
                }
            }

            @Override
            public void onLambdaFail(String reason) {
                if (callback != null) {
                    callback.onFail(reason);
                }
            }
        });
    }

    public void sendMessage(@NonNull final Conversation conversation,
                            @Nullable final String body,
                            @Nullable final Asset asset,
                            @Nullable final JSONObject metadata,
                            @Nullable final SaveCallback<Message> callback) {
        if (!StringUtils.isEmpty(body) || asset != null || metadata != null) {
            Record record = new Record("message");
            Reference reference = new Reference("conversation", conversation.getId());
            record.set("conversation_id", reference);
            if (body != null) {
                record.set("body", body);
            }
            if (metadata != null) {
                record.set("metadata", metadata);
            }

            if (asset == null) {
                this.saveMessageRecord(record, callback);
            } else {
                this.saveMessageRecord(record, asset, callback);
            }
        } else {
            if (callback != null) {
                callback.onFail("Please provide either body, asset or metadata");
            }
        }
    }

    private void saveMessageRecord(final Record message,
                                   @Nullable final SaveCallback<Message> callback) {
        Database publicDB = this.skygear.getPublicDatabase();
        publicDB.save(message, new SaveResponseAdapter<Message>(callback) {
            @Override
            public Message convert(Record record) {
                return new Message(record);
            }
        });
    }

    private void saveMessageRecord(final Record message,
                                   final Asset asset,
                                   @Nullable final SaveCallback<Message> callback) {
        this.skygear.uploadAsset(asset, new AssetPostRequest.ResponseHandler() {
            @Override
            public void onPostSuccess(Asset asset, String response) {
                message.set("attachment", asset);
                ChatContainer.this.saveMessageRecord(message, callback);
            }

            @Override
            public void onPostFail(Asset asset, String reason) {
                ChatContainer.this.saveMessageRecord(message, callback);
            }
        });
    }

    /* --- Chat User --- */

    public void getChatUsers(@Nullable final GetCallback<List<ChatUser>> callback) {
        Query query = new Query("user");
        Database publicDB = this.skygear.getPublicDatabase();
        publicDB.query(query, new QueryResponseAdapter<List<ChatUser>>(callback) {
            @Override
            public List<ChatUser> convert(Record[] records) {
                List<ChatUser> users = new ArrayList<>(records.length);

                for (Record record : records) {
                    users.add(new ChatUser(record));
                }

                return users;
            }
        });
    }

    /* --- Subscription--- */

    public void subscribeConversationMessage(@NonNull final Conversation conversation,
                                             @Nullable final MessageSubscriptionCallback callback) {
        final Pubsub pubsub = this.skygear.getPubsub();
        final String conversationId = conversation.getId();

        if (messageSubscription.get(conversationId) == null) {
            getOrCreateUserChannel(new GetCallback<Record>() {
                @Override
                public void onSucc(@Nullable Record userChannelRecord) {
                    if (userChannelRecord != null) {
                        Subscription subscription = new Subscription(
                                conversationId,
                                (String) userChannelRecord.get("name"),
                                callback
                        );
                        subscription.attach(pubsub);
                        messageSubscription.put(conversationId, subscription);
                    }
                }

                @Override
                public void onFail(@Nullable String failReason) {

                }
            });
        } else {
            throw new InvalidParameterException("Don't subscribe messages for a conversation more than once");
        }
    }

    public void unsubscribeConversationMessage(@NonNull final Conversation conversation) {
        final Pubsub pubsub = this.skygear.getPubsub();
        String conversationId = conversation.getId();
        Subscription subscription = messageSubscription.get(conversationId);

        if (subscription != null) {
            subscription.detach(pubsub);
            messageSubscription.remove(conversationId);
        } else {
            throw new InvalidParameterException("Don't unsubscribe messages for a conversation more than once");
        }
    }

    private void getOrCreateUserChannel(@Nullable final GetCallback<Record> callback) {
        try {
            Query query = new Query("user_channel");
            Database privateDatabase = this.skygear.getPrivateDatabase();
            privateDatabase.query(query, new RecordQueryResponseHandler() {
                @Override
                public void onQuerySuccess(Record[] records) {
                    if (records.length != 0) {
                        if (callback != null) {
                            callback.onSucc(records[0]);
                        }
                    } else {
                        createUserChannel(callback);
                    }
                }

                @Override
                public void onQueryError(String reason) {
                    if (callback != null) {
                        callback.onFail(reason);
                    }
                }
            });
        } catch (AuthenticationException e) {
            if (callback != null) {
                callback.onFail(e.getMessage());
            }
        }
    }

    private void createUserChannel(final GetCallback<Record> callback) {
        try {
            Record conversation = new Record("user_channel");
            conversation.set("name", UUID.randomUUID().toString());

            RecordSaveResponseHandler handler = new RecordSaveResponseHandler() {
                @Override
                public void onSaveSuccess(Record[] records) {
                    Record record = records[0];
                    if (callback != null) {
                        callback.onSucc(record);
                    }
                }

                @Override
                public void onPartiallySaveSuccess(
                        Map<String, Record> successRecords,
                        Map<String, String> reasons) {

                }

                @Override
                public void onSaveFail(String reason) {
                    if (callback != null) {
                        callback.onFail(reason);
                    }
                }
            };

            Database db = this.skygear.getPrivateDatabase();
            db.save(conversation, handler);
        } catch (AuthenticationException e) {
            callback.onFail(e.getMessage());
        }
    }

    /* --- Unread --- */

    public void getTotalUnread(@Nullable final GetCallback<Unread> callback) {
        this.skygear.callLambdaFunction("chat:total_unread", null, new LambdaResponseHandler() {
            @Override
            public void onLambdaSuccess(JSONObject result) {
                try {
                    int count = result.getInt("message");
                    if (callback != null) {
                        callback.onSucc(new Unread(count));
                    }
                } catch (JSONException e) {
                    if (callback != null) {
                        callback.onFail(e.getMessage());
                    }
                }
            }

            @Override
            public void onLambdaFail(String reason) {
                if (callback != null) {
                    callback.onFail(reason);
                }
            }
        });
    }
}
