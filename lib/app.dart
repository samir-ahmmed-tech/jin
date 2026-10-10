import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/core/theme/app_theme.dart';
import 'package:jin/providers/settings_provider.dart';
import 'package:jin/screens/onboarding_screen.dart';
import 'package:jin/screens/home_screen.dart';

class JinApp extends ConsumerWidget {
  const JinApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);

    return MaterialApp(
      title: 'JIN',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      home: settings.isLoading
          ? const _SplashScreen()
          : settings.apiKeys.isEmpty
              ? const OnboardingScreen()
              : const HomeScreen(),
    );
  }
}

/// Splash screen shown while settings are loading
class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0A14),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.smart_toy_rounded,
              size: 80,
              color: const Color(0xFF4FC3F7),
            ),
            const SizedBox(height: 24),
            Text(
              'JIN',
              style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                    letterSpacing: 4,
                  ),
            ),
            const SizedBox(height: 16),
            const CircularProgressIndicator(
              color: Color(0xFF4FC3F7),
            ),
          ],
        ),
      ),
    );
  }
}
