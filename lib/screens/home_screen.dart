import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/providers/chat_provider.dart';
import 'package:jin/providers/settings_provider.dart';
import 'package:jin/screens/chat_screen.dart';
import 'package:jin/screens/settings_screen.dart';
import 'package:jin/widgets/model_selector.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(chatProvider.notifier).loadSessions();
    });
  }

  @override
  Widget build(BuildContext context) {
    final chatState = ref.watch(chatProvider);
    final sessions = chatState.sessions;

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A14),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1A1A2E),
        elevation: 0,
        title: const Text(
          'JIN',
          style: TextStyle(
            fontWeight: FontWeight.bold,
            letterSpacing: 2,
          ),
        ),
        actions: const [
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 8.0),
            child: ModelSelector(),
          ),
        ],
      ),
      drawer: Drawer(
        backgroundColor: const Color(0xFF1A1A2E),
        child: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    const Icon(Icons.smart_toy_rounded, color: Color(0xFF4FC3F7), size: 32),
                    const SizedBox(width: 12),
                    const Text(
                      'JIN',
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                        letterSpacing: 2,
                      ),
                    ),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.close, color: Colors.white54),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: ElevatedButton.icon(
                  onPressed: () {
                    ref.read(chatProvider.notifier).createSession();
                    Navigator.pop(context);
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('New Chat'),
                  style: ElevatedButton.styleFrom(
                    minimumSize: const Size(double.infinity, 48),
                    backgroundColor: const Color(0xFF4FC3F7),
                    foregroundColor: const Color(0xFF0A0A14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: sessions.isEmpty
                    ? const Center(
                        child: Text(
                          'No recent chats',
                          style: TextStyle(color: Colors.white54),
                        ),
                      )
                    : ListView.builder(
                        itemCount: sessions.length,
                        itemBuilder: (context, index) {
                          final session = sessions[index];
                          return Dismissible(
                            key: Key(session.id),
                            background: Container(
                              color: Colors.red.withOpacity(0.8),
                              alignment: Alignment.centerRight,
                              padding: const EdgeInsets.only(right: 20),
                              child: const Icon(Icons.delete, color: Colors.white),
                            ),
                            direction: DismissDirection.endToStart,
                            onDismissed: (_) {
                              ref.read(chatProvider.notifier).deleteSession(session.id);
                            },
                            child: ListTile(
                              selected: session.id == chatState.currentSessionId,
                              selectedTileColor: const Color(0xFF0A0A14),
                              leading: Icon(
                                session.isPinned ? Icons.push_pin : Icons.chat_bubble_outline,
                                color: session.isPinned ? const Color(0xFF4FC3F7) : Colors.white54,
                                size: 20,
                              ),
                              title: Text(
                                session.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(color: Colors.white),
                              ),
                              subtitle: Text(
                                '${session.messageCount} messages',
                                style: const TextStyle(color: Colors.white38, fontSize: 12),
                              ),
                              onTap: () {
                                ref.read(chatProvider.notifier).selectSession(session.id);
                                Navigator.pop(context);
                              },
                              onLongPress: () {
                                ref.read(chatProvider.notifier).togglePinSession(session.id);
                              },
                            ),
                          );
                        },
                      ),
              ),
              const Divider(color: Colors.white12),
              ListTile(
                leading: const Icon(Icons.settings, color: Colors.white70),
                title: const Text('Settings', style: TextStyle(color: Colors.white70)),
                onTap: () {
                  Navigator.pop(context);
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const SettingsScreen()),
                  );
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
      body: sessions.isEmpty && chatState.currentSessionId == null
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.smart_toy_rounded,
                    size: 80,
                    color: Color(0xFF1A1A2E),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Welcome to JIN',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Colors.white54,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Start a new chat to begin',
                    style: TextStyle(color: Colors.white38),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton(
                    onPressed: () {
                      ref.read(chatProvider.notifier).createSession();
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF4FC3F7),
                      foregroundColor: const Color(0xFF0A0A14),
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                    ),
                    child: const Text('New Chat'),
                  ),
                ],
              ),
            )
          : const ChatScreen(),
    );
  }
}
