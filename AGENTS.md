# Bater Ponto — contexto para agentes

## Objetivo
Aplicativo Android pessoal para substituir uma planilha de cálculo de jornada.
O usuário registra os horários e consulta quando completa sua meta diária e quando
atinge o limite de 10 horas trabalhadas. O app também calcula o descanso entre
jornadas e apresenta notificações.

Manter a experiência simples, agradável, em português brasileiro, com uma tela
rolável em Jetpack Compose e suporte a temas claro e escuro. Não há backend,
login ou integração com o sistema de ponto da empresa. Registrar aqui não bate
o ponto na empresa. As referências de horas são parâmetros do app, não uma
garantia de conformidade trabalhista.

## Regras do produto
- Meta diária padrão: 8h, ajustável. O limite máximo é fixo em 10h e não deve
  ser apresentado como uma meta sugerida.
- Contar apenas os períodos trabalhados; descontar almoço e saídas temporárias.
- Distinguir entrada inicial, saída para almoço, retorno do almoço, saída
  temporária, retorno ao trabalho e saída definitiva.
- Aceitar vários pares de saída/retorno, inclusive retorno para fazer HE.
  HE continua na mesma jornada; não reiniciar a contagem de 10h.
- Almoço padrão: 11:30–13:00, opcional e confirmado pelo usuário.
  Ao salvar um almoço manual, substituir o automático daquele dia.
  Nunca descontar períodos sobrepostos duas vezes.
- Durante uma saída manual ainda sem retorno, congelar o total trabalhado e
  não inventar o horário de retorno para calcular metas ainda não atingidas.
- Uma meta já atingida conserva o horário real em que foi atingida.
- Horários possuem data; preservar jornadas que atravessam a meia-noite.
- Registros devem ser estritamente crescentes, no passado ou presente na UI.
  O modelo limita o intervalo entre primeiro e último registro a menos de 24h.
- Entrada de horário: quatro dígitos, máscara HH:mm, teclado numérico,
  horas de 00 a 23 e minutos de 00 a 59.
- Lista expansível "Pontos do dia": mostrar registros manuais e intervalos
  automáticos; identificar horários automáticos futuros como previstos.
  Horários manuais podem ser corrigidos; exclusão disponível para o último.
- Saída definitiva encerra o contador e os avisos de limite. Permitir reabrir.
  Depois de encerrar, esconder previsões de saída e mostrar total, saída real
  e diferença para o limite de 10h.
- Descanso padrão: 11h, configurável. A notificação "Entrada liberada" usa a
  saída definitiva real. Antes de encerrar, o horário mostrado é uma previsão.
- "Quando posso entrar?" só aparece se o horário calculado for estritamente
  posterior a 08:00, o horário habitual do usuário. Exatamente 08:00 também
  oculta o cartão. Quando a jornada está encerrada, esse cartão fica no topo.
- O filtro das 08:00 é visual; atualmente não suprime o alarme de descanso.
  Não alterar essa regra de notificação sem um pedido explícito.
- "Começar nova jornada" deve estar também no cartão de próxima entrada.
  Pedir confirmação: limpa os registros atuais e preserva preferências.
  O app não mantém histórico de jornadas anteriores. Não prometer histórico.
  A última saída definitiva é preservada separadamente para o alarme de
  descanso; após reset, o cartão antigo fica oculto e a tela volta ao início.
- Manter acesso à configuração de descanso mesmo com o cartão oculto.

## Exemplos de referência
1. Entrada 08:00, almoço 11:30–13:00:
   meta de 8h às 17:30; limite de 10h às 19:30.
2. Entrada 08:21, almoço 11:30–13:00, saída temporária 17:47, retorno 19:50:
   meta de 8h às 19:54; limite de 10h às 21:54.
3. Encerrando o exemplo 2 às 21:50:
   9h56 trabalhadas, 4 minutos antes do limite, entrada após descanso de 11h
   às 08:50 do dia seguinte.
4. Descanso terminando às 06:00, 07:00 ou 08:00:
   não exibir o cartão de próxima entrada.

## Arquitetura atual
Módulo único `:app`; pacote `com.victorhugo.baterponto`.
Fontes em `app/src/main/java/com/victorhugo/baterponto/`.

- `WorkDay.kt`: modelo imutável, validação, períodos trabalhados, metas,
  descanso, tipos de registro e reset.
- `DayPoint.kt`: linha da lista; índice nulo significa horário automático.
- `WorkDayStore.kt`: persistência local em SharedPreferences.
- `WorkDayScreen.kt`: tela Compose, cartões, lista e diálogos.
- `MainActivity.kt`: estado da tela, ciclo de vida, permissões e reagendamento.
- `TimeInput.kt`: validação incremental, conversão e máscara de horários.
- `WorkAlerts.kt`: AlarmManager, notificações de limite/descanso e receivers.
- `JourneyProgress.kt`: regras puras de exibição e faixas de cor.
- `JourneyNotification.kt`: notificação customizada, barra e agendamento.
- `JourneyProgressService.kt`: serviço foreground para atualização do progresso.
- `ui/theme/`: cores, tipografia e temas.
- `FinishedDayPreview.kt` e previews na tela: conferência visual.
- `app/src/main/res/layout/notification_journey*.xml`: RemoteViews Android;
  notificações não são renderizadas com Compose.
- Testes de cálculo em `app/src/test/java/com/victorhugo/baterponto/`.

Não trocar toda a arquitetura para fazer ajustes pequenos. Separar cálculo puro
de APIs Android. Preservar dados e preferências existentes ao acrescentar campos:
definir padrões compatíveis e cuidar dos índices dos pares saída/retorno.

## Notificações e segundo plano
- Alertas de limite: antecedência de 10, 15 ou 30 minutos e ao completar 10h.
- Progresso: desativado, últimos 15 minutos de trabalho ou jornada inteira.
- Cores: verde abaixo de 8h; amarelo de 8h a 9h; laranja de 9h a 9h45;
  vermelho a partir de 9h45. A barra fica limitada visualmente a 100%.
- A notificação de progresso é silenciosa e desconta intervalos.
- Atualizações não podem depender apenas de um timer da Activity.
- Reagendar após editar registros/preferências e eventos de reboot/relógio.
  Cancelar alarmes obsoletos; impedir notificações duplicadas.
- Pedir permissão de notificações no contexto da funcionalidade. Explicar
  quando alarmes precisos estiverem desativados.
- O serviço declara tipo foreground `specialUse`; não substituir por tipos
  inadequados (por exemplo, dataSync) apenas para contornar restrições.
- Respeitar Não perturbe, canais bloqueados e restrições do sistema.
- Não afirmar que notificações foram testadas em aparelho quando só houve
  build, testes unitários ou preview. Doze, reinício, bloqueio de tela,
  revogação de permissões e aparência exigem verificação no dispositivo.

## Desenvolvimento e validação
- Usar ferramentas MCP do Android Studio para ler/editar arquivos; preservar
  buffers e mudanças do usuário. Não editar fontes por comandos de shell.
- Preferir Kotlin e Material 3 existentes. Evitar dependências para problemas
  que o projeto já resolve.
- Testar cálculos alterados com JUnit local: almoço automático/manual,
  sobreposição, retorno para HE, meia-noite, encerramento, limites e descanso.
- Na raiz, pelo terminal configurado do Android Studio:
  - Windows: `.\gradlew.bat :app:testDebugUnitTest`
  - Build: `.\gradlew.bat :app:assembleDebug`
  - Inspeções: `.\gradlew.bat :app:lintDebug`
  - Teste focado: acrescentar `--tests com.victorhugo.baterponto.NomeDoTeste`.
- No Linux/macOS, usar `./gradlew`.
- Conferir previews para mudanças visuais. Por padrão, executar apenas testes
  unitários; não instalar/alterar dados do aparelho sem necessidade.
- Última validação da implementação nesta sessão: 37 testes unitários passaram.
  Isso é uma referência histórica, não substitui executar os testes afetados.

## Git e documentação
- Versionar fontes, recursos, testes, Gradle Wrapper e documentação.
- Não versionar builds, APKs, caches, configuração local da IDE, local.properties,
  credenciais, tokens, arquivos de assinatura ou planilhas pessoais/da empresa.
- Não copiar caminhos pessoais nem os dados da planilha para o repositório.
  Os exemplos acima bastam para reproduzir os cálculos.
- Atualizar este documento e o README quando regras ou fluxos mudarem.
- Não reescrever histórico, forçar push ou publicar releases sem pedido.
