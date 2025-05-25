---
author: 
  - ref: timothypratley
date: 2025-05-06
category: data
tags: [data-science, noj]
keywords: [noj]
---

Transcript of [Noj reload executable](https://youtu.be/tDz1x2d65C0)

Hello code champs, number ninjas and data divers!

Imagine being able to experiment with Clojure without needing to install tooling, set up editors, or even know about deps.edn
Sounds like a dream, right?
Well, dream no more—meet Noj's new Code Reload Executable!

We can skip the Clojure tooling and editor setup,
but we do need to install the recommended Adoptive Java.
Now we download the latest Noj jar from the Scicloj Noj GitHub releases page.
Let’s check the jar is in the Downloads directory,
and that we have java installed by executing `java -version`.

Launch Noj using `java -jar` and the path to the jar file.
Pro tip, if you press tab while typing a path, it will autocomplete.
Noj is running, it created a directory called notebooks, and it recommends we create a Clojure file there.

I’ll create my file with Notepad,
being careful to create a .clj file rather than a text file.
When I saved that file, a browser window opened.
Let’s put them side by side.
Every time I save the file, the notebook updates.

If I’m interested in one particular top-level form,
I can narrow the output by adding ,, anywhere in the form.
And removing it renders the full notebook.
And check this out—comments are rendered as Markdown. How cool is that?
What an easy way to get coding!

Noj isn’t just a notebook—it’s a fully featured data science environment.
It includes interesting datasets that can be queried and the results shown as tables or even better as charts.

Noj makes experimenting with Clojure easier than ever.
No setup headaches, no complicated tooling—just pure coding joy.

Until next time,
Keep on coding
