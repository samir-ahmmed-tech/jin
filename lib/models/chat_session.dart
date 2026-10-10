import 'package:uuid/uuid.dart';

class ChatSession {
  final String id;
  String title;
  final DateTime createdAt;
  DateTime updatedAt;
  String? defaultModelId;
  bool isPinned;
  int messageCount;

  ChatSession({
    String? id,
    required this.title,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.defaultModelId,
    this.isPinned = false,
    this.messageCount = 0,
  }) : id = id ?? const Uuid().v4(),
       createdAt = createdAt ?? DateTime.now(),
       updatedAt = updatedAt ?? DateTime.now();

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'createdAt': createdAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
    if (defaultModelId != null) 'defaultModelId': defaultModelId,
    'isPinned': isPinned,
    'messageCount': messageCount,
  };

  factory ChatSession.fromJson(Map<String, dynamic> json) => ChatSession(
    id: json['id'] as String,
    title: json['title'] as String,
    createdAt: DateTime.parse(json['createdAt'] as String),
    updatedAt: DateTime.parse(json['updatedAt'] as String),
    defaultModelId: json['defaultModelId'] as String?,
    isPinned: json['isPinned'] as bool? ?? false,
    messageCount: json['messageCount'] as int? ?? 0,
  );
}
