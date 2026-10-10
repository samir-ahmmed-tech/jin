import 'package:uuid/uuid.dart';

enum MessageRole { user, assistant, system, tool }

class ToolCall {
  final String id;
  final String name;
  final Map<String, dynamic> arguments;
  String? result;

  ToolCall({
    required this.id,
    required this.name,
    required this.arguments,
    this.result,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'arguments': arguments,
    if (result != null) 'result': result,
  };

  factory ToolCall.fromJson(Map<String, dynamic> json) => ToolCall(
    id: json['id'] as String,
    name: json['name'] as String,
    arguments: Map<String, dynamic>.from(json['arguments'] as Map),
    result: json['result'] as String?,
  );
}

class ChatMessage {
  final String id;
  final MessageRole role;
  final String content;
  final DateTime timestamp;
  final String? modelId;
  final List<ToolCall>? toolCalls;
  final String? toolCallId;
  final bool isError;
  bool isStreaming;

  ChatMessage({
    String? id,
    required this.role,
    required this.content,
    DateTime? timestamp,
    this.modelId,
    this.toolCalls,
    this.toolCallId,
    this.isError = false,
    this.isStreaming = false,
  }) : id = id ?? const Uuid().v4(),
       timestamp = timestamp ?? DateTime.now();

  ChatMessage copyWith({
    String? id,
    MessageRole? role,
    String? content,
    DateTime? timestamp,
    String? modelId,
    List<ToolCall>? toolCalls,
    String? toolCallId,
    bool? isError,
    bool? isStreaming,
  }) => ChatMessage(
    id: id ?? this.id,
    role: role ?? this.role,
    content: content ?? this.content,
    timestamp: timestamp ?? this.timestamp,
    modelId: modelId ?? this.modelId,
    toolCalls: toolCalls ?? this.toolCalls,
    toolCallId: toolCallId ?? this.toolCallId,
    isError: isError ?? this.isError,
    isStreaming: isStreaming ?? this.isStreaming,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'role': role.name,
    'content': content,
    'timestamp': timestamp.toIso8601String(),
    if (modelId != null) 'modelId': modelId,
    if (toolCalls != null) 'toolCalls': toolCalls!.map((t) => t.toJson()).toList(),
    if (toolCallId != null) 'toolCallId': toolCallId,
    'isError': isError,
  };

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
    id: json['id'] as String,
    role: MessageRole.values.byName(json['role'] as String),
    content: json['content'] as String,
    timestamp: DateTime.parse(json['timestamp'] as String),
    modelId: json['modelId'] as String?,
    toolCalls: json['toolCalls'] != null
        ? (json['toolCalls'] as List).map((t) => ToolCall.fromJson(t as Map<String, dynamic>)).toList()
        : null,
    toolCallId: json['toolCallId'] as String?,
    isError: json['isError'] as bool? ?? false,
  );
}
