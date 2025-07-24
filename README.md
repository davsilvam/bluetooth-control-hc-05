# Documentação do Projeto Bluetooth Control HC-05

## Visão Geral

Este projeto é um aplicativo Android para controle de dispositivos via Bluetooth, utilizando o
módulo HC-05. Ele permite enviar comandos de texto pelo terminal ou comandos de movimento via botões
direcionais (D-Pad).

## Estrutura de Pastas

- `app/src/main/java/de/kai_morich/simple_bluetooth_terminal/`
    - `ui/`: Fragments e Activities da interface.
    - `service/`: Serviço de comunicação serial Bluetooth.
    - `bluetooth/`: Utilitários e socket Bluetooth.
    - `utils/`: Utilitários gerais.

- `app/src/main/res/layout/`: Layouts XML das telas.
- `app/src/main/res/drawable/`: Ícones e backgrounds.
- `app/src/main/AndroidManifest.xml`: Manifesto do app.

## Principais Telas

- **DevicesFragment**: Lista dispositivos Bluetooth disponíveis para conexão.
- **TerminalActivity**: Gerencia as abas de controle e terminal.
- **ControlsFragment**: Aba de botões direcionais para enviar comandos de movimento.
- **TerminalLogFragment**: Aba de terminal para envio/recebimento de comandos de texto.

## Fluxo de Funcionamento

1. Usuário seleciona um dispositivo Bluetooth na lista.
2. Abre a tela principal com duas abas:
    - **Controle**: Botões direcionais (D-Pad) para comandos rápidos.
    - **Terminal**: Campo de texto para comandos manuais e visualização do log.
3. Comunicação com o dispositivo é feita via `SerialService`, que gerencia a conexão e troca de
   dados.

## Principais Classes

- `SerialService`: Serviço responsável pela conexão e troca de dados Bluetooth.
- `SerialSocket`: Implementa o socket Bluetooth SPP.
- `SerialListener`: Interface para eventos de comunicação serial.

## Permissões

O app solicita permissões Bluetooth conforme a versão do Android, incluindo `BLUETOOTH_CONNECT` para
Android 12+.

## Observações

- O layout e lógica dos botões direcionais estão apenas em `ControlsFragment`.
- O terminal de texto está em `TerminalLogFragment`.
- O fragmento `TerminalFragment` foi removido por estar depreciado.

## Como rodar o projeto

1. **Pré-requisitos**
    - Android Studio (recomendado Narwhal 2025.1.1 ou superior)
    - Dispositivo Android ou emulador com Bluetooth (preferencialmente físico)
    - Módulo Bluetooth HC-05 para testes reais

2. **Clonando o repositório**
   ```sh
   git clone https://github.com/davsilvam/bluetooth-control-hc-05.git
   ```

   ```sh
   cd bluetooth-control-hc-05
   ```

3. **Abrindo no Android Studio**
    - Abra o Android Studio.
    - Selecione `File > Open` e escolha a pasta do projeto.

4. **Compilando e executando**
    - Clique em `Run` ou pressione `Shift + F10`.
    - Escolha um dispositivo físico ou emulador.

5. **Permissões**
    - O app solicitará permissões Bluetooth ao iniciar.
    - Para Android 12+, aceite a permissão `BLUETOOTH_CONNECT`.

6. **Uso**
    - Na tela inicial, selecione um dispositivo Bluetooth emparelhado.
    - Use as abas para enviar comandos pelo terminal ou pelos botões de controle.
