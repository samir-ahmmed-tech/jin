import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/models/chat_session.dart';
import 'package:jin/models/ai_provider.dart';

class StorageService {
  static final StorageService instance = StorageService._();
  StorageService._();

  late Box _sessionsBox;
  late Box _messagesBox;
  late Box _settingsBox;
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  Future<void> init() async {
    _sessionsBox = await Hive.openBox('sessions');
    _messagesBox = await Hive.openBox('messages');
    _settingsBox = await Hive.openBox('settings');
  }

  // API Keys (encrypted)
  Future<void> saveApiKey(AIProviderType provider, String key) async {
    await _secureStorage.write(key: 'api_key_${provider.name}', value: key);
  }
  
  Future<String?> getApiKey(AIProviderType provider) async {
    return await _secureStorage.read(key: 'api_key_${provider.name}');
  }
  
  Future<void> deleteApiKey(AIProviderType provider) async {
    await _secureStorage.delete(key: 'api_key_${provider.name}');
  }
  
  Future<bool> hasAnyApiKey() async {
    for (final provider in AIProviderType.values) {
      final key = await getApiKey(provider);
      if (key != null && key.isNotEmpty) return true;
    }
    return false;
  }

  // Chat Sessions
  Future<List<ChatSession>> getSessions() async {
    final List<ChatSession> sessions = [];
    for (final key in _sessionsBox.keys) {
      final json = _sessionsBox.get(key);
      if (json != null) {
        sessions.add(ChatSession.fromJson(Map<String, dynamic>.from(jsonDecode(json as String))));
      }
    }
    sessions.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return sessions;
  }
  
  Future<void> saveSession(ChatSession session) async {
    await _sessionsBox.put(session.id, jsonEncode(session.toJson()));
  }
  
  Future<void> deleteSession(String sessionId) async {
    await _sessionsBox.delete(sessionId);
    await _messagesBox.delete(sessionId);
  }

  // Messages
  Future<List<ChatMessage>> getMessages(String sessionId) async {
    final raw = _messagesBox.get(sessionId);
    if (raw == null) return [];
    final List<dynamic> list = jsonDecode(raw as String);
    return list.map((e) => ChatMessage.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }
  
  Future<void> saveMessages(String sessionId, List<ChatMessage> messages) async {
    await _messagesBox.put(sessionId, jsonEncode(messages.map((m) => m.toJson()).toList()));
  }
  
  Future<void> addMessage(String sessionId, ChatMessage message) async {
    final messages = await getMessages(sessionId);
    messages.add(message);
    await saveMessages(sessionId, messages);
  }
  
  Future<void> clearMessages(String sessionId) async {
    await _messagesBox.delete(sessionId);
  }

  // Settings
  Future<void> saveSetting(String key, String value) async {
    await _settingsBox.put(key, value);
  }
  
  Future<String?> getSetting(String key) async {
    return _settingsBox.get(key) as String?;
  }
  
  Future<bool> isOnboardingComplete() async {
    return _settingsBox.get('onboarding_complete') == 'true';
  }
  
  Future<void> setOnboardingComplete() async {
    await _settingsBox.put('onboarding_complete', 'true');
  }
}
