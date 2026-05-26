---
name: yyym-note-style
description: Learn yyym's personal Chinese technical-note writing style from local Markdown notes, summarize the style in three core points, and transform new technical material, code snippets, algorithms, operations logs, or study content into notes that match that style. Use when the user asks to analyze their notes, imitate their note style, generate personal study notes, convert material into yyym-style notes, or follow the two-stage workflow of style learning then note generation.
---

# YYYM Note Style

## Purpose

Use this skill to write Chinese technical notes in yyym's observed style. The task usually has two stages:

- Learn: read representative local notes, infer the stable style, then reply exactly with `风格已分析，我总结出你的写作特点是 ...` followed by 3 core points.
- Generate: after the user provides new material, output the note directly in the learned style.

If the current turn only asks to create or update this skill, maintain the skill. If the user asks for a note, use the workflow below.

## Learning Workflow

Read the user's provided notes or the current folder's Markdown notes. Prefer technical notes, bug-fix notes, operations logs, algorithm notes, and study notes. Sample across categories when possible instead of reading only one file.

When analyzing, look for:

- how concepts are introduced;
- whether the note starts from a problem, a command, a code path, or a definition;
- how code blocks, formulas, images, links, and tables are used;
- where the writer explains details versus skips obvious parts;
- recurring words such as `我们`, `可以`, `实际上`, `要注意`, `不难发现`, `于是`, `这里`;
- local imperfections that make the note feel real: typos, casual transitions, "先记着", "到时候再看", "这次直接成功了", and similar fragments.

After learning, respond in this exact shape:

```text
风格已分析，我总结出你的写作特点是 [3个核心点]。
```

Keep the three points concrete. Avoid generic labels like "逻辑清晰" unless tied to specific evidence.

## Style Profile

Write like a technical learner who is making the idea usable for themselves, not like a polished tutorial author.

Core traits:

- Start close to the thing itself. A note may begin with a definition, a project file path, an error, a command, or "不想排查到底..." style decision. Do not add a ceremonial opening.
- Use dense technical nouns naturally: `section`, `.text`, `.data`, `GOT`, `ELF`, `LabelEncoder`, `router.go`, `smtp.163.com:25`, and similar terms should appear inline with Chinese explanation.
- Explain by walking through the actual process: command -> output/file -> why it matters -> next operation. Concrete implementation details beat abstract summaries.
- Prefer "我们..." for derivations and study notes; prefer direct operational sentences for bug/ops notes.
- Let paragraph length follow importance. Spend many lines on a key derivation or code path; handle simple settings in one sentence.
- Use Markdown pragmatically: headings, short numbered lists, blockquotes for source/parameter explanation, code blocks with minimal comments, formulas where useful, and image links if the source notes use them.
- Keep a slightly rough "原生笔记" feel. It is okay to include small asides like "这里要注意", "这个文件无法直接查看", "到时候再看链接", or "不想继续排查就换方案".

## Generation Rules

When generating a note:

- Title must be direct and task-oriented, such as `KMP 算法`, `Hertz 路由注册`, `修复邮箱无法发送邮件的问题`, or `CSAPP-链接`.
- Body starts immediately. Do not write background filler.
- Use natural transitions: `我们来看`, `这里`, `于是`, `不过`, `同时`, `要注意`, `进一步的`, `如果...那么...`.
- Do not force symmetrical sections. Do not balance every paragraph just for neatness.
- Include actual code/commands from the material when available. Explain parameters beside the code or in blockquotes.
- Preserve useful source links if supplied, but do not make the whole note a link summary.
- For bug-fix or operations material, record the chosen fix and the concrete files/config/database columns touched.
- For algorithms or math, show the critical relation/formula first, then reason through why the transition works.
- For code comments, keep them short and directional. Avoid comment prose that repeats the line.

## Forbidden Habits

Avoid AI/tutorial smell:

- Do not use `首先`, `其次`, `最后`, `综上所述`, `总而言之`, or `不仅...而且...`.
- Do not end with grand conclusions, slogans, or "this is more than a tool..." style elevation.
- Do not pad with definitions before the real problem.
- Do not over-polish into a symmetric outline if the material is naturally uneven.
- Do not flatten every sentence into long declarative prose. Mix short sentences, questions, and quick reminders.

## Output Format

For the learning stage, only output the required style-analysis sentence.

For the generation stage:

Use this shape:

- `# 直接、有力的标题`
- Body starts from the concrete problem, command, concept, or code path.
- Fenced code block with the original code or command.
- Optional blockquote for parameter, output, or pitfall explanation.

Use fenced code blocks for code and commands. Use native Markdown math for formulas. Keep the final paragraph practical; stop when the note has captured the implementation logic or key pitfall.
