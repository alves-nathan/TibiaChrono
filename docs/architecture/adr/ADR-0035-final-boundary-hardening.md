# ADR-0035: Final architecture boundary hardening before feature work

## Status
Accepted

## Context

TibiaChrono passou por uma sequência de refactors arquiteturais para reduzir classes grandes, separar facades de componentes internos, mover acesso a persistência para ports e proteger decisões com regras ArchUnit.

Depois dos splits dos services, parsers e adapters de persistência, o risco principal antes de voltar ao desenvolvimento de features é regressão arquitetural: novas mudanças podem voltar a acoplar camadas de aplicação/web diretamente a detalhes de scraping, JSoup, JDBC ou adapters concretos.

## Decision

Adicionar um hardening final de boundaries com regras ArchUnit de alto nível:

- application e config não devem depender de implementações de scraper nem de JSoup;
- web adapters não devem depender diretamente de scraper, JSoup, JDBC ou `java.sql`;
- scraper adapters não devem depender de application services nem de persistence adapters.

Essas regras complementam os boundaries específicos já existentes para facades e repository ports.

## Consequences

Novas features devem manter o fluxo hexagonal esperado:

- web adapters chamam casos de uso/facades de aplicação;
- application usa ports de domínio e serviços próprios;
- scraper adapters implementam ports e mantêm detalhes de HTML/JSoup encapsulados;
- persistence adapters permanecem atrás de ports ou de read models específicos.

Quando um caso legítimo precisar cruzar esses boundaries, a decisão deve ser registrada em novo ADR antes de relaxar a regra.
