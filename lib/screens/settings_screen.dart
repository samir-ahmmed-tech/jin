import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/core/constants/app_constants.dart';
import 'package:jin/models/ai_provider.dart';
import 'package:jin/providers/settings_provider.dart';
import 'package:jin/providers/chat_provider.dart';
import 'package:jin/widgets/confirmation_dialog.dart';
import 'package:jin/widgets/model_selector.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final settingsNotifier = ref.read(settingsProvider.notifier);

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A14),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Settings'),
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 16),
        children: [
          _buildSectionHeader('AI Models'),
          ListTile(
            title: const Text('Current Model', style: TextStyle(color: Colors.white)),
            subtitle: const Text('Select default AI model', style: TextStyle(color: Colors.white54)),
            trailing: const ModelSelector(),
          ),
          
          const Divider(color: Colors.white12, height: 32),
          _buildSectionHeader('API Keys'),
          ...AIProviderType.values.map((provider) {
            final hasKey = settingsNotifier.hasApiKey(provider);
            return Card(
              color: const Color(0xFF1A1A2E),
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              child: ListTile(
                leading: Text(provider.iconAsset, style: const TextStyle(fontSize: 24)),
                title: Text(provider.displayName, style: const TextStyle(color: Colors.white)),
                subtitle: Text(
                  hasKey ? 'Configured' : 'Not configured',
                  style: TextStyle(color: hasKey ? Colors.green : Colors.redAccent),
                ),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (hasKey)
                      IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
                        onPressed: () => settingsNotifier.removeApiKey(provider),
                      ),
                    IconButton(
                      icon: Icon(hasKey ? Icons.edit : Icons.add, color: const Color(0xFF4FC3F7)),
                      onPressed: () => _showApiKeyDialog(context, ref, provider),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
          
          const Divider(color: Colors.white12, height: 32),
          _buildSectionHeader('Danger Zone'),
          ListTile(
            leading: const Icon(Icons.delete_sweep, color: Colors.redAccent),
            title: const Text('Clear All Chats', style: TextStyle(color: Colors.redAccent)),
            onTap: () async {
              final confirm = await ConfirmationDialog.show(
                context,
                title: 'Clear All Chats',
                message: 'Are you sure you want to delete all chat history? This action cannot be undone.',
                isDestructive: true,
              );
              if (confirm) {
                // Future enhancement: clear all via chatProvider (only clear messages exists on provider right now)
                ref.read(chatProvider.notifier).clearMessages(); 
              }
            },
          ),
          
          const Divider(color: Colors.white12, height: 32),
          _buildSectionHeader('About'),
          ListTile(
            leading: const Icon(Icons.info_outline, color: Colors.white54),
            title: const Text('Version', style: TextStyle(color: Colors.white)),
            trailing: const Text(AppConstants.appVersion, style: TextStyle(color: Colors.white54)),
          ),
          const SizedBox(height: 16),
          const Center(
            child: Text(
              'JIN - ${AppConstants.appTagline}',
              style: TextStyle(color: Colors.white38, fontSize: 12),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 16, bottom: 8, top: 8),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          color: Color(0xFF4FC3F7),
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
          fontSize: 12,
        ),
      ),
    );
  }

  void _showApiKeyDialog(BuildContext context, WidgetRef ref, AIProviderType provider) {
    final controller = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: Text('Enter ${provider.displayName} API Key', style: const TextStyle(color: Colors.white)),
        content: TextField(
          controller: controller,
          obscureText: true,
          style: const TextStyle(color: Colors.white),
          decoration: InputDecoration(
            hintText: 'sk-...',
            hintStyle: const TextStyle(color: Colors.white38),
            filled: true,
            fillColor: const Color(0xFF0A0A14),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel', style: TextStyle(color: Colors.white54)),
          ),
          ElevatedButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                ref.read(settingsProvider.notifier).setApiKey(provider, controller.text.trim());
              }
              Navigator.pop(ctx);
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF4FC3F7),
              foregroundColor: const Color(0xFF0A0A14),
            ),
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}
