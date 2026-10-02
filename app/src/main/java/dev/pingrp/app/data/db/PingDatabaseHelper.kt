package dev.pingrp.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.pingrp.app.data.model.ApiProvider
import dev.pingrp.app.data.model.Character
import dev.pingrp.app.data.model.CharacterOnlineStatus
import dev.pingrp.app.data.model.Message
import dev.pingrp.app.data.model.MessageStatus
import dev.pingrp.app.data.model.SenderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PingDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "ping_roleplay.db"
        const val DATABASE_VERSION = 2

        const val TABLE_CHARACTERS = "characters"
        const val TABLE_MESSAGES = "messages"

        // Characters Columns
        const val COL_CHAR_ID = "id"
        const val COL_CHAR_NAME = "name"
        const val COL_CHAR_AVATAR = "avatar_url"
        const val COL_CHAR_PERSONALITY = "personality"
        const val COL_CHAR_SCENARIO = "scenario"
        const val COL_CHAR_STATUS = "online_status"
        const val COL_CHAR_LAST_SEEN = "last_seen_timestamp"
        const val COL_CHAR_NEXT_CHECK = "next_check_timestamp"
        const val COL_CHAR_PATIENCE = "patience_level"
        const val COL_CHAR_DELAY_MULT = "reply_delay_multiplier"
        const val COL_CHAR_PROVIDER = "provider"
        const val COL_CHAR_MODEL = "model_name"
        const val COL_CHAR_LAST_ERROR = "last_error_message"

        // Messages Columns
        const val COL_MSG_ID = "id"
        const val COL_MSG_CHAR_ID = "character_id"
        const val COL_MSG_SENDER = "sender"
        const val COL_MSG_TEXT = "text"
        const val COL_MSG_TIMESTAMP = "timestamp"
        const val COL_MSG_STATUS = "status"
        const val COL_MSG_IS_THOUGHT = "is_thought"
        const val COL_MSG_THOUGHT_TEXT = "thought_text"
    }

    private val _databaseUpdates = MutableStateFlow(System.currentTimeMillis())
    val databaseUpdates: StateFlow<Long> = _databaseUpdates.asStateFlow()

    private fun notifyChanged() {
        _databaseUpdates.value = System.currentTimeMillis()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_CHARACTERS (
                $COL_CHAR_ID TEXT PRIMARY KEY,
                $COL_CHAR_NAME TEXT NOT NULL,
                $COL_CHAR_AVATAR TEXT,
                $COL_CHAR_PERSONALITY TEXT NOT NULL,
                $COL_CHAR_SCENARIO TEXT,
                $COL_CHAR_STATUS TEXT NOT NULL,
                $COL_CHAR_LAST_SEEN INTEGER NOT NULL,
                $COL_CHAR_NEXT_CHECK INTEGER NOT NULL,
                $COL_CHAR_PATIENCE INTEGER NOT NULL,
                $COL_CHAR_DELAY_MULT REAL NOT NULL,
                $COL_CHAR_PROVIDER TEXT NOT NULL,
                $COL_CHAR_MODEL TEXT NOT NULL,
                $COL_CHAR_LAST_ERROR TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID TEXT PRIMARY KEY,
                $COL_MSG_CHAR_ID TEXT NOT NULL,
                $COL_MSG_SENDER TEXT NOT NULL,
                $COL_MSG_TEXT TEXT NOT NULL,
                $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                $COL_MSG_STATUS TEXT NOT NULL,
                $COL_MSG_IS_THOUGHT INTEGER NOT NULL DEFAULT 0,
                $COL_MSG_THOUGHT_TEXT TEXT,
                FOREIGN KEY ($COL_MSG_CHAR_ID) REFERENCES $TABLE_CHARACTERS ($COL_CHAR_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        // Insert default initial character
        insertInitialCharacter(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_CHARACTERS ADD COLUMN $COL_CHAR_LAST_ERROR TEXT;")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun insertInitialCharacter(db: SQLiteDatabase) {
        val initialChar = Character(
            id = "default_mita",
            name = "Мика",
            avatarUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            personality = "Твоя близкая подруга детства, немного цундере, вспыльчивая, но заботливая. Любит аниме, мемы и постоянно зависает в телефоне. Если долго не отвечаешь, начинает ворчать и спрашивать, где ты пропадаешь.",
            scenario = "Обычный будний вечер. Вы переписываетесь в мессенджере после учебы/работы.",
            onlineStatus = CharacterOnlineStatus.ONLINE,
            lastSeenTimestamp = System.currentTimeMillis(),
            nextCheckTimestamp = 0L,
            patienceLevel = 4,
            replyDelayMultiplier = 1.0f,
            provider = ApiProvider.GROQ,
            modelName = "qwen/qwen3.8-27b"
        )
        insertCharacterDirect(db, initialChar)

        val firstMsg = Message(
            id = "msg_init_1",
            characterId = initialChar.id,
            sender = SenderType.CHARACTER,
            text = "Хей! Ты дома уже? Я тут новую мангу нашла, просто отвал башки, ты обязан заценить!",
            timestamp = System.currentTimeMillis() - 60000,
            status = MessageStatus.SEEN
        )
        insertMessageDirect(db, firstMsg)
    }

    // ---------------- Characters CRUD ----------------

    fun insertCharacter(character: Character) {
        val db = writableDatabase
        insertCharacterDirect(db, character)
        notifyChanged()
    }

    private fun insertCharacterDirect(db: SQLiteDatabase, character: Character) {
        val values = ContentValues().apply {
            put(COL_CHAR_ID, character.id)
            put(COL_CHAR_NAME, character.name)
            put(COL_CHAR_AVATAR, character.avatarUrl)
            put(COL_CHAR_PERSONALITY, character.personality)
            put(COL_CHAR_SCENARIO, character.scenario)
            put(COL_CHAR_STATUS, character.onlineStatus.name)
            put(COL_CHAR_LAST_SEEN, character.lastSeenTimestamp)
            put(COL_CHAR_NEXT_CHECK, character.nextCheckTimestamp)
            put(COL_CHAR_PATIENCE, character.patienceLevel)
            put(COL_CHAR_DELAY_MULT, character.replyDelayMultiplier)
            put(COL_CHAR_PROVIDER, character.provider.name)
            put(COL_CHAR_MODEL, character.modelName)
            put(COL_CHAR_LAST_ERROR, character.lastErrorMessage)
        }
        db.insertWithOnConflict(TABLE_CHARACTERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCharacter(character: Character) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CHAR_NAME, character.name)
            put(COL_CHAR_AVATAR, character.avatarUrl)
            put(COL_CHAR_PERSONALITY, character.personality)
            put(COL_CHAR_SCENARIO, character.scenario)
            put(COL_CHAR_STATUS, character.onlineStatus.name)
            put(COL_CHAR_LAST_SEEN, character.lastSeenTimestamp)
            put(COL_CHAR_NEXT_CHECK, character.nextCheckTimestamp)
            put(COL_CHAR_PATIENCE, character.patienceLevel)
            put(COL_CHAR_DELAY_MULT, character.replyDelayMultiplier)
            put(COL_CHAR_PROVIDER, character.provider.name)
            put(COL_CHAR_MODEL, character.modelName)
            put(COL_CHAR_LAST_ERROR, character.lastErrorMessage)
        }
        db.update(TABLE_CHARACTERS, values, "$COL_CHAR_ID = ?", arrayOf(character.id))
        notifyChanged()
    }

    fun updateCharacterError(characterId: String, errorMessage: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CHAR_LAST_ERROR, errorMessage)
        }
        db.update(TABLE_CHARACTERS, values, "$COL_CHAR_ID = ?", arrayOf(characterId))
        notifyChanged()
    }

    fun updateCharacterStatus(characterId: String, status: CharacterOnlineStatus, nextCheckTimestamp: Long = 0L) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CHAR_STATUS, status.name)
            put(COL_CHAR_LAST_SEEN, System.currentTimeMillis())
            if (nextCheckTimestamp > 0L) {
                put(COL_CHAR_NEXT_CHECK, nextCheckTimestamp)
            }
        }
        db.update(TABLE_CHARACTERS, values, "$COL_CHAR_ID = ?", arrayOf(characterId))
        notifyChanged()
    }

    fun getCharacter(characterId: String): Character? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHARACTERS, null,
            "$COL_CHAR_ID = ?", arrayOf(characterId),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToCharacter(it) else null
        }
    }

    fun getAllCharacters(): List<Character> {
        val db = readableDatabase
        val list = mutableListOf<Character>()
        val cursor = db.query(TABLE_CHARACTERS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val char = cursorToCharacter(it)
                // Attach last message and unread count
                val lastMsg = getLastMessageForCharacter(char.id)
                val unreadCount = getUnreadCount(char.id)
                list.add(
                    char.copy(
                        lastMessageText = lastMsg?.text ?: "",
                        lastMessageTime = lastMsg?.timestamp ?: char.lastSeenTimestamp,
                        unreadCount = unreadCount
                    )
                )
            }
        }
        return list.sortedByDescending { it.lastMessageTime }
    }

    fun deleteCharacter(characterId: String) {
        val db = writableDatabase
        db.delete(TABLE_CHARACTERS, "$COL_CHAR_ID = ?", arrayOf(characterId))
        db.delete(TABLE_MESSAGES, "$COL_MSG_CHAR_ID = ?", arrayOf(characterId))
        notifyChanged()
    }

    // ---------------- Messages CRUD ----------------

    fun insertMessage(message: Message) {
        val db = writableDatabase
        insertMessageDirect(db, message)
        notifyChanged()
    }

    private fun insertMessageDirect(db: SQLiteDatabase, message: Message) {
        val values = ContentValues().apply {
            put(COL_MSG_ID, message.id)
            put(COL_MSG_CHAR_ID, message.characterId)
            put(COL_MSG_SENDER, message.sender.name)
            put(COL_MSG_TEXT, message.text)
            put(COL_MSG_TIMESTAMP, message.timestamp)
            put(COL_MSG_STATUS, message.status.name)
            put(COL_MSG_IS_THOUGHT, if (message.isThought) 1 else 0)
            put(COL_MSG_THOUGHT_TEXT, message.thoughtText)
        }
        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMessagesForCharacter(characterId: String): List<Message> {
        val db = readableDatabase
        val list = mutableListOf<Message>()
        val cursor = db.query(
            TABLE_MESSAGES, null,
            "$COL_MSG_CHAR_ID = ?", arrayOf(characterId),
            null, null, "$COL_MSG_TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMessage(it))
            }
        }
        return list
    }

    fun getLastMessageForCharacter(characterId: String): Message? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES, null,
            "$COL_MSG_CHAR_ID = ?", arrayOf(characterId),
            null, null, "$COL_MSG_TIMESTAMP DESC", "1"
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToMessage(it) else null
        }
    }

    fun markMessagesAsSeen(characterId: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATUS, MessageStatus.SEEN.name)
        }
        db.update(
            TABLE_MESSAGES, values,
            "$COL_MSG_CHAR_ID = ? AND $COL_MSG_SENDER = ? AND $COL_MSG_STATUS != ?",
            arrayOf(characterId, SenderType.CHARACTER.name, MessageStatus.SEEN.name)
        )
        notifyChanged()
    }

    fun markUserMessagesAsSeen(characterId: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATUS, MessageStatus.SEEN.name)
        }
        db.update(
            TABLE_MESSAGES, values,
            "$COL_MSG_CHAR_ID = ? AND $COL_MSG_SENDER = ? AND $COL_MSG_STATUS != ?",
            arrayOf(characterId, SenderType.USER.name, MessageStatus.SEEN.name)
        )
        notifyChanged()
    }

    private fun getUnreadCount(characterId: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COL_MSG_CHAR_ID = ? AND $COL_MSG_SENDER = ? AND $COL_MSG_STATUS != ?",
            arrayOf(characterId, SenderType.CHARACTER.name, MessageStatus.SEEN.name)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    // ---------------- Cursor Mappers ----------------

    private fun cursorToCharacter(c: Cursor): Character {
        return Character(
            id = c.getString(c.getColumnIndexOrThrow(COL_CHAR_ID)),
            name = c.getString(c.getColumnIndexOrThrow(COL_CHAR_NAME)),
            avatarUrl = c.getString(c.getColumnIndexOrThrow(COL_CHAR_AVATAR)) ?: "",
            personality = c.getString(c.getColumnIndexOrThrow(COL_CHAR_PERSONALITY)),
            scenario = c.getString(c.getColumnIndexOrThrow(COL_CHAR_SCENARIO)) ?: "",
            onlineStatus = try {
                CharacterOnlineStatus.valueOf(c.getString(c.getColumnIndexOrThrow(COL_CHAR_STATUS)))
            } catch (_: Exception) {
                CharacterOnlineStatus.ONLINE
            },
            lastSeenTimestamp = c.getLong(c.getColumnIndexOrThrow(COL_CHAR_LAST_SEEN)),
            nextCheckTimestamp = c.getLong(c.getColumnIndexOrThrow(COL_CHAR_NEXT_CHECK)),
            patienceLevel = c.getInt(c.getColumnIndexOrThrow(COL_CHAR_PATIENCE)),
            replyDelayMultiplier = c.getFloat(c.getColumnIndexOrThrow(COL_CHAR_DELAY_MULT)),
            provider = try {
                ApiProvider.valueOf(c.getString(c.getColumnIndexOrThrow(COL_CHAR_PROVIDER)))
            } catch (_: Exception) {
                ApiProvider.GROQ
            },
            modelName = c.getString(c.getColumnIndexOrThrow(COL_CHAR_MODEL)) ?: "qwen/qwen3.8-27b",
            lastErrorMessage = try {
                val idx = c.getColumnIndex(COL_CHAR_LAST_ERROR)
                if (idx >= 0) c.getString(idx) else null
            } catch (_: Exception) { null }
        )
    }

    private fun cursorToMessage(c: Cursor): Message {
        return Message(
            id = c.getString(c.getColumnIndexOrThrow(COL_MSG_ID)),
            characterId = c.getString(c.getColumnIndexOrThrow(COL_MSG_CHAR_ID)),
            sender = try {
                SenderType.valueOf(c.getString(c.getColumnIndexOrThrow(COL_MSG_SENDER)))
            } catch (_: Exception) {
                SenderType.CHARACTER
            },
            text = c.getString(c.getColumnIndexOrThrow(COL_MSG_TEXT)),
            timestamp = c.getLong(c.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
            status = try {
                MessageStatus.valueOf(c.getString(c.getColumnIndexOrThrow(COL_MSG_STATUS)))
            } catch (_: Exception) {
                MessageStatus.SENT
            },
            isThought = c.getInt(c.getColumnIndexOrThrow(COL_MSG_IS_THOUGHT)) == 1,
            thoughtText = c.getString(c.getColumnIndexOrThrow(COL_MSG_THOUGHT_TEXT))
        )
    }
}
