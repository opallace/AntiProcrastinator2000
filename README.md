# Anti-Procrastinator 2000

Anti-Procrastinator 2000 é um app Android experimental feito em Kotlin com Jetpack Compose para bloquear o uso do celular por um período definido pelo usuário.

O app usa **Device Owner + Lock Task Mode** para impedir que o usuário saia da tela de bloqueio enquanto o tempo não termina.

> Projeto experimental. Use com cuidado. Sempre teste primeiro com períodos curtos, como 1 minuto.


## Funcionalidades

- Seleção de tempo de bloqueio.
- Bloqueio imediato do dispositivo.
- Modo kiosk usando Lock Task Mode.
- Cronômetro regressivo na tela de bloqueio.
- Liberação automática ao fim do tempo.
- Persistência do bloqueio após reinicialização.
- Histórico simples de tempo economizado.
- Configuração como Device Owner via ADB.

## Requisitos

- Android Studio.
- ADB instalado e configurado.
- Dispositivo Android compatível com Device Owner.
- Depuração USB ativada.
- Dispositivo recém-resetado para configurar Device Owner pela primeira vez.


## Atenção sobre Device Owner

Para o bloqueio forte funcionar, o app precisa ser configurado como **Device Owner**.

Normalmente, o comando `set-device-owner` só funciona em um dispositivo recém-resetado e sem contas configuradas no momento da configuração.

Depois que o app já estiver configurado como Device Owner, você pode adicionar uma conta Google normalmente.

## Comandos principais para instalar, configurar e testar

Execute estes comandos na raiz do projeto.

### 1. Instalar o APK debug

```bash
adb install -t -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Definir o app como Device Owner

```bash
adb shell dpm set-device-owner com.example.anti_procrastinator2000/.MyDeviceAdminReceiver
```

Se funcionar, a saída deve ser parecida com:

```text
Success: Device owner set to package com.example.anti_procrastinator2000/.MyDeviceAdminReceiver
Active admin set to component com.example.anti_procrastinator2000/.MyDeviceAdminReceiver
```

### 3. Acompanhar os logs do app

```bash
adb logcat | grep AntiProcrastinator2000
```
## Comandos úteis

### Verificar se o app está como Device Owner

```bash
adb shell dumpsys device_policy | grep -i -A 20 -B 10 anti_procrastinator
```

### Listar pacotes relacionados ao app

```bash
adb shell pm list packages | grep procrastinator
```

### Ver HOME activities

```bash
adb shell cmd package get-home-activities
```

### Ver dispositivos conectados ao ADB

```bash
adb devices
```

## Observações importantes

Sem Device Owner, o app não consegue fazer o bloqueio forte. Ele pode até entrar em modo de fixação de tela, mas o usuário ainda consegue sair usando recursos do sistema.

Com Device Owner + Lock Task Mode, o app consegue bloquear o uso normal do celular até o fim do tempo configurado.

Ainda assim, ações físicas extremas podem escapar do controle do app, como:

- desligamento forçado;
- bootloader/recovery;
- factory reset;
- descarregamento da bateria.


## Status

Projeto experimental para estudo de Android, Device Owner, Lock Task Mode, AlarmManager e Jetpack Compose.
