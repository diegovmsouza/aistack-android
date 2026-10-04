export const meta = {
  name: 'onda2b-retomada',
  description: 'Retoma F1, F3, F4 e F5 da Onda 2 do app Android (a máquina reiniciou no meio)',
  phases: [{ title: 'Telas' }],
}
const SCHEMA = {
  type: 'object',
  properties: {
    resumo: { type: 'string' },
    arquivos: { type: 'array', items: { type: 'string' } },
    buildVerde: { type: 'boolean' },
    testes: { type: 'string' },
    pendencias: { type: 'array', items: { type: 'string' } },
  },
  required: ['resumo', 'arquivos', 'buildVerde', 'testes', 'pendencias'],
}
const RETOMADA = `RETOMADA: a máquina reiniciou às 22:57 e interrompeu sua execução anterior desta mesma tarefa. Seu trabalho parcial JÁ ESTÁ no disco (git status mostra os arquivos novos/alterados; os da sua propriedade são seus). Não recomece do zero: leia o que existe nos SEUS caminhos, complete o que falta e deixe o build verde. O F2 (chat) já terminou: ChatScreen usa ChatComposer; não edite arquivos dele. Ferramentas de leitura e escrita estão liberadas (modo sem prompts).\n\n`
const out = await parallel(['F1', 'F3', 'F4', 'F5'].map(k => () =>
  agent(RETOMADA + `Sua tarefa original, na íntegra, está em /home/diego/Documents/aistack-android/docs/turnolongo/onda2/tarefa-${k}.md: leia-a inteira antes de qualquer coisa e siga-a.`, { label: k, phase: 'Telas', schema: SCHEMA, effort: 'high' })
    .then(r => r && { feature: k, ...r })))
return out.filter(Boolean)
