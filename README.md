# Bater Ponto

Aplicativo Android pessoal para acompanhar a jornada e saber a hora de sair.
Feito em Kotlin com Jetpack Compose e Material 3, com temas claro e escuro.

## O que faz
- Calcula a saída para a meta diária (8h por padrão) e o limite de 10h.
- Registra entrada, almoço, saídas temporárias, retornos e saída definitiva.
- Oferece almoço automático das 11:30 às 13:00 ou registro manual.
- Mostra os pontos do dia em uma lista expansível, com edição de horários.
- Desconta intervalos sem duplicar períodos sobrepostos.
- Avisa quando o limite de trabalho se aproxima.
- Exibe notificação de progresso nos últimos 15 minutos ou na jornada inteira.
- Calcula a próxima entrada após o descanso, com padrão configurável de 11h,
  e pode notificar "Entrada liberada".
- Destaca a próxima entrada quando ela for depois das 08:00.
- Salva os registros localmente, sem conta ou servidor.

O app auxilia o cálculo pessoal; ele **não registra ponto na empresa**.
As referências configuradas não substituem as regras aplicáveis à jornada.

## Exemplo
Entrada às 08:21, almoço das 11:30 às 13:00, saída às 17:47 e retorno às 19:50:
o limite de 10h ocorre às **21:54**.

Se a saída definitiva for às 21:50, o total será **9h56** e o descanso de 11h
terminará às **08:50 do dia seguinte**.

## Executar
1. Abra a raiz do projeto no Android Studio.
2. Instale o SDK solicitado pelo projeto e sincronize o Gradle.
3. Use o JDK compatível configurado no Android Studio.
4. Execute o módulo `app` em um dispositivo ou emulador Android 12 ou superior.

Configuração atual: minSdk 31, compileSdk/targetSdk 37. Use o Gradle Wrapper
incluído no projeto; não é necessário instalar Gradle separadamente.
O arquivo `local.properties` com o caminho do SDK pertence à máquina local
e não deve ser enviado ao Git.

## Validar
No PowerShell, na raiz do projeto:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

No Linux/macOS, substitua `.\gradlew.bat` por `./gradlew`.

## Uso das notificações
Permita notificações e, para avisos no horário, autorize alarmes precisos
nas configurações apresentadas pelo app. Som e vibração respeitam os canais
do Android e o modo Não perturbe. A notificação de progresso é silenciosa.

O funcionamento com tela bloqueada e as restrições de bateria precisam ser
conferidos no dispositivo usado. O filtro visual das 08:00 não desativa
automaticamente o aviso de fim do descanso.

## Dados
"Começar nova jornada" apaga os registros atuais após confirmação, mantendo
preferências. Ainda não existe histórico de jornadas. A última saída definitiva
é preservada para o lembrete de descanso até o início de uma nova jornada.

## Para continuar o desenvolvimento
Leia [AGENTS.md](AGENTS.md): regras do produto, mapa dos arquivos, exemplos de
cálculo e orientações de validação.
