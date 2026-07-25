# Sankhya-W: Rotina de Envio de E-mail com Anexos

Este projeto é uma extensão server-side (Ação de Rotina Java) para a plataforma **Sankhya-W**. Ele implementa a lógica de um Botão de Ação customizado responsável por processar registros selecionados na tela, anexar arquivos armazenados no banco de dados, aplicar regras de negócio (como a remoção de metadados customizados de arquivos BLOB) e registrar o e-mail na Fila de Mensagens nativa do Sankhya (TMDFMG).

---

## 🚀 Funcionalidades

* **Integração JAPE:** Utiliza o framework JAPE do Sankhya para consultas de banco seguras (NativeSql e QueryExecutor) e persistência de dados (DynamicVO, JapeWrapper).
* **Processamento Otimizado de BLOBs:** Inspeciona arquivos (BLOBs) diretamente do banco de dados, lendo apenas os bytes iniciais para extrair metadados via Expressões Regulares (Regex), evitando o carregamento de arquivos pesados inteiros em memória.
* **Higienização de Arquivos:** Identifica e remove cabeçalhos de metadados customizados (`__start_fileinformation__...`) garantindo a integridade binária dos arquivos antes do envio por e-mail.
* **Tratamento de Múltiplos Anexos:** Vincula dinamicamente múltiplos arquivos à tabela de anexos nativa (TMDFMA e TSIAXM).
* **Arquitetura em Camadas:** Código limpo e modularizado, separando responsabilidades entre Models, Utils, Services e o Controlador Principal.

---

## 📂 Estrutura do Projeto

O projeto foi refatorado utilizando os princípios de separação de responsabilidades (SoC - Separation of Concerns):

```text
src/
└── br/
    └── com/
        └── sankhya/
            ├── BotaoEnviaEmail.java         # Controlador principal (Ação do Botão)
            │
            ├── service/                     # Regras de Negócio e acesso JAPE
            │   ├── EmailService.java        # Gestão de Contas SMTP, Modelos e Fila de Mensagem
            │   ├── AnexoService.java        # Busca e vinculação de anexos
            │   └── BlobService.java         # Limpeza e extração de bytes (BLOB)
            │
            ├── model/                       # DTOs para transporte de dados
            │   ├── Anexo.java               # Agregador de arquivo e metadados
            │   └── FileInfo.java            # Metadados extraídos (Nome e MIME Type)
            │
            └── util/                        # Ferramentas independentes genéricas
                └── AnalisadorDeBlob.java    # Parser de metadados de BLOB via Regex
```
## 🛠️ Tecnologias e Dependências

* **Java:** 1.8 (Padrão para ambientes WildFly/JBoss do Sankhya)
* **Frameworks/APIs Sankhya:** 
  * `br.com.sankhya.extensions.actionbutton.*`
  * `br.com.sankhya.jape.*`
  * `br.com.sankhya.modelcore.util.*`
  * `br.com.sankhya.ws.ServiceContext`
* **JSON:** Biblioteca Gson (`com.google.gson.*`) utilizada nativamente pelo contexto do Sankhya.

> **Nota:** Nenhuma ferramenta de automação de build (como Maven ou Gradle) ou biblioteca externa é necessária, a fim de evitar conflitos de Classpath no servidor de aplicação do cliente. O projeto utiliza apenas bibliotecas padrões da JRE e do próprio Sankhya-W.

---

## ⚙️ Como Implantar no Sankhya-W

1. Compile todos os arquivos `.java` deste projeto, garantindo que o Classpath contenha as bibliotecas do servidor Sankhya (`sankhya-extensions.jar`, `jape.jar`, etc.).
2. Empacote as classes compiladas em um arquivo `.jar` mantendo a estrutura de pacotes `br.com.sankhya.*`.
3. No Sankhya-W, navegue até a tela **Dicionário de Dados > Ações** (ou **Construtor de Telas > Botões de Ação**).
4. Crie uma nova Ação do tipo **Rotina Java**.
5. Faça o upload do arquivo `.jar`.
6. No campo "Classe a ser executada", informe o pacote completo da classe controladora:
   `br.com.sankhya.BotaoEnviaEmail`
7. Salve e teste o botão na tela de destino.

---

## 🛡️ Tratamento de Exceções

O projeto utiliza a classe `JapeSession` para garantir que todas as interações com o banco de dados ocorram em um ambiente transacional. Falhas em qualquer etapa (falta de preenchimento de campos obrigatórios, erros de SQL, ou arquivos corrompidos) farão *rollback* seguro, retornando a mensagem de erro formatada de volta para a interface do usuário sem causar vazamentos de memória (Resource Leaks).

---

## 👨‍💻 Autoria

Desenvolvido e mantido por:

**Felipe Salles Lopes**  
✉️ felipe.lopes@sankhya.com.br