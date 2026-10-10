import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/models/chat_session.dart';
import 'package:jin/services/storage_service.dart';
import 'package:jin/core/constants/app_constants.dart';
import 'package:jin/providers/ai_service_provider.dart';
import 'package:uuid/uuid.dart';

final chatProvider = ChangeNotifierProvider<ChatNotifier>((ref) {
  return ChatNotifier(ref);
});

class ChatNotifier extends ChangeNotifier {
  final Ref _ref;
  
  List<ChatSession> _sessions = [];
  String? _currentSessionId;
  Map<String, List<ChatMessage>> _messages = {};
  bool _isGenerating = false;

  List<ChatSession> get sessions => _sessions;
  String? get currentSessionId => _currentSessionId;
  Map<String, List<ChatMessage>> get messages => _messages;
  bool get isGenerating => _isGenerating;

  final _uuid = const Uuid();

  ChatNotifier(this._ref) {
    loadSessions();
  }

  Future<void> loadSessions() async {
    _sessions = await StorageService.instance.getSessions();
    notifyListeners();
  }

  Future<ChatSession> createSession({String? title}) async {
    final newSession = ChatSession(
      id: _uuid.v4(),
      title: title ?? 'New Chat',
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
    _sessions.insert(0, newSession);
    await StorageService.instance.saveSession(newSession);
    
    _currentSessionId = newSession.id;
    _messages[newSession.id] = [];
    
    notifyListeners();
    return newSession;
  }

  Future<void> selectSession(String sessionId) async {
    _currentSessionId = sessionId;
    if (!_messages.containsKey(sessionId)) {
      _messages[sessionId] = await StorageService.instance.getMessages(sessionId);
    }
    notifyListeners();
  }

  Future<void> deleteSession(String sessionId) async {
    _sessions.removeWhere((s) => s.id == sessionId);
    _messages.remove(sessionId);
    if (_currentSessionId == sessionId) {
      _currentSessionId = null;
    }
    await StorageService.instance.deleteSession(sessionId);
    notifyListeners();
  }

  Future<void> togglePinSession(String sessionId) async {
    final index = _sessions.indexWhere((s) => s.id == sessionId);
    if (index != -1) {
      final session = _sessions[index];
      _sessions[index] = session.copyWith(isPinned: !session.isPinned);
      await StorageService.instance.saveSession(_sessions[index]);
      notifyListeners();
    }
  }

  Future<void> renameSession(String sessionId, String newTitle) async {
    final index = _sessions.indexWhere((s) => s.id == sessionId);
    if (index != -1) {
      _sessions[index] = _sessions[index].copyWith(title: newTitle);
      await StorageService.instance.saveSession(_sessions[index]);
      notifyListeners();
    }
  }

  List<ChatMessage> getCurrentMessages() {
    if (_currentSessionId == null) return [];
    return _messages[_currentSessionId] ?? [];
  }

  Future<void> sendMessage(String content) async {
    if (content.trim().isEmpty) return;

    if (_currentSessionId == null) {
      final words = content.split(' ');
      final title = words.take(4).join(' ') + (words.length > 4 ? '...' : '');
      await createSession(title: title.isEmpty ? 'New Chat' : title);
    }

    final sessionId = _currentSessionId!;
    
    final userMessage = ChatMessage(
      id: _uuid.v4(),
      role: MessageRole.user,
      content: content,
      timestamp: DateTime.now(),
    );

    _messages[sessionId] = [...(_messages[sessionId] ?? []), userMessage];
    
    _isGenerating = true;
    notifyListeners();

    final placeholderAssistantId = _uuid.v4();
    var assistantMessage = ChatMessage(
      id: placeholderAssistantId,
      role: MessageRole.assistant,
      content: '',
      timestamp: DateTime.now(),
      isStreaming: true,
    );

    _messages[sessionId] = [..._messages[sessionId]!, assistantMessage];
    notifyListeners();

    final aiService = _ref.read(aiServiceProvider);

    if (aiService == null) {
      _updateAssistantMessage(
        sessionId, 
        placeholderAssistantId, 
        'No API key configured for the selected model. Please add it in Settings.',
        isError: true,
      );
      _isGenerating = false;
      notifyListeners();
      return;
    }

    try {
      final response = await aiService.sendMessage(
        messages: _messages[sessionId]!.where((m) => m.id != placeholderAssistantId).toList(),
        systemPrompt: AppConstants.defaultSystemPrompt,
      );

      _updateAssistantMessage(
        sessionId, 
        placeholderAssistantId, 
        response.content,
      );
    } catch (e) {
      _updateAssistantMessage(
        sessionId, 
        placeholderAssistantId, 
        'Error: ${e.toString()}',
        isError: true,
      );
    }

    _isGenerating = false;
    
    await StorageService.instance.saveMessages(sessionId, _messages[sessionId]!);
    
    final sessionIndex = _sessions.indexWhere((s) => s.id == sessionId);
    if (sessionIndex != -1) {
      _sessions[sessionIndex] = _sessions[sessionIndex].copyWith(
        updatedAt: DateTime.now(),
        messageCount: _messages[sessionId]!.length,
      );
      await StorageService.instance.saveSession(_sessions[sessionIndex]);
    }

    notifyListeners();
  }

  void _updateAssistantMessage(String sessionId, String messageId, String content, {bool isError = false}) {
    final msgs = _messages[sessionId]!;
    final index = msgs.indexWhere((m) => m.id == messageId);
    if (index != -1) {
      msgs[index] = msgs[index].copyWith(
        content: content,
        isStreaming: false,
        isError: isError,
      );
    }
  }

  Future<void> clearMessages(String sessionId) async {
    _messages[sessionId] = [];
    await StorageService.instance.saveMessages(sessionId, []);
    
    final sessionIndex = _sessions.indexWhere((s) => s.id == sessionId);
    if (sessionIndex != -1) {
      _sessions[sessionIndex] = _sessions[sessionIndex].copyWith(
        updatedAt: DateTime.now(),
        messageCount: 0,
      );
      await StorageService.instance.saveSession(_sessions[sessionIndex]);
    }
    
    notifyListeners();
  }

  Future<void> clearAllChats() async {
    for (final s in List.from(_sessions)) {
      await StorageService.instance.deleteSession(s.id);
    }
    _sessions.clear();
    _currentSessionId = null;
    _messages.clear();
    notifyListeners();
  }
}
