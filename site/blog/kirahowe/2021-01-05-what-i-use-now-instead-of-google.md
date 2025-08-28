---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
title: What I Use Now Instead Of Google
date: 2021-01-05
site/group: Tech
site/tags:
  - ungoogle
  - software
  - nextcloud
---

I made a goal for myself in January 2020 to stop using Google products by the end of the year. That might sound like way too generous a timeline, but Google owned pretty much all of my data at that point, so it was a fairly large project. Plus I'm a slow and steady kind of person. I know if I give myself a generous enough timeline I can accomplish even things that seem too hard for me at first.

## First, Why

Since I got into programming about 5 years ago, I kept hearing all these bad things about Google and how horrible of a company it is from other tech people. It always seemed a bit exaggerated to me, but the evidence has been piling up over the years. Learning about [their involvement](https://gizmodo.com/google-is-helping-the-pentagon-build-ai-for-drones-1823464533) with the US military's [Algorithmic Warfare Cross-Functional Team (Project Maven)](https://dodcio.defense.gov/Portals/0/Documents/Project%20Maven%20DSD%20Memo%2020170425.pdf) was the last straw for me <sup id="footnote-1"><a href= "https://kiramclean.com/blog/what-i-use-now-instead-of-google/#footnote-1-text">1</a></sup>, but even before I learned about that I was pretty uncomfortable with a lot of what I heard. In Google's defence they eventually did [pull out of the project](https://web.archive.org/web/20210101083904/https://www.nytimes.com/2018/06/01/technology/google-pentagon-project-maven.html) after [massive backlash by thousands of employees](https://web.archive.org/web/20201231204850/https://www.nytimes.com/2018/04/04/technology/google-letter-ceo-pentagon-project.html).

Still, I'm pretty convinced now that their continued existence is a catastrophe not only for public safety, but also for [the environment](https://www.vox.com/recode/2020/1/3/21030688/google-amazon-ai-oil-gas), [gender equity](https://www.nytimes.com/2018/11/01/technology/google-walkout-sexual-harassment.html), [the economy](https://www.telegraph.co.uk/technology/google/9739039/Googles-tax-avoidance-is-called-capitalism-says-chairman-Eric-Schmidt.html), [fair labour practices](https://www.vice.com/en/article/jgexe8/google-fired-an-engineer-who-wrote-code-telling-googlers-they-had-a-right-to-organize), [privacy](https://www.stallman.org/google.html#surveillance), [journalism](https://www.nature.com/articles/s41562-020-00954-0), [democracy](https://www.judiciary.senate.gov/download/epstein-testimony), [race relations](https://www.vox.com/2018/4/3/17168256/google-racism-algorithms-technology), and the [project of civilization itself](https://www.scientificamerican.com/article/big-tech-out-of-control-capitalism-and-the-end-of-civilization/). But anyway, the point of this post isn't to motivate you to also quit Google. I'll tell you how I really feel some other time.

The rest of this post is about the tools and services I replaced all the Google things with. I did mostly accomplish my goal, with a few caveats which I describe in the relevant sections.

## Replacements

Here's the short version:
- GMail → [ProtonMail](https://protonmail.com/signup)
- Chrome → [Firefox developer edition](https://www.mozilla.org/en-US/firefox/developer/)
- Google search → [DuckDuckGo](https://duckduckgo.com/)
- Google Drive → [Sync](https://www.sync.com/) and [Backblaze](https://www.backblaze.com/b2/cloud-storage.html)
- Google DNS → [Cloudflare DNS](https://blog.cloudflare.com/announcing-1111/)
- Maps → Apple maps
- YouTube → Netflix, [TED talks](https://www.ted.com/), conference archives, and still a little YouTube (anonymously in a [Firefox container](https://addons.mozilla.org/en-US/firefox/addon/multi-account-containers/))
- Google Analytics → [Fathom](https://usefathom.com/)
- Everything else (calendar, reminders, photos, docs, video chat, news feeds) → [Nextcloud](https://nextcloud.com/signup/), running [on my own instance](https://kiramclean.com/blog/how-to-set-up-your-own-nextcloud-server/)

And here's the long version:

### GMail → [ProtonMail](https://protonmail.com/signup)

ProtonMail has free accounts, but I pay for the lowest level that allows use of a custom domain with it (€48/year) so I don't have to change my email address ever again if I want to switch providers. It was quite a pain to change my email address all over the place, but I started in May and just did it slowly over time. I highly recommend decoupling your email address from your email provider if you're considering switching. First of all an email from a custom domain seems more, not less, serious than a gmail address to most people. But the main reason is just so you don't have to change your email address ever again if (when) you want to switch to some newer, better email provider.

I set up forwarding from my old GMail account to my new email address then filtered my mail for anything sent to the old address. If it was someone or some place I wanted to continue hearing from, I updated my email address with them. If not I unsubscribed. This turned out to be a wonderful opportunity to purge my newsletter and other email subscriptions. Also I'm happy to report most companies are now (finally) respecting unsubscribe requests. Two (looking at you Geektastic and Rakuten) continued to spam me after I requested they stop, so now I filter out all their mail as spam.

### Chrome → Firefox developer edition

I primarily use [Firefox developer edition](https://www.mozilla.org/en-US/firefox/developer/) now for my browser, including for work. The developer tools are just as good as Chrome's for the kind of work I do.

There are some web apps that literally or effectively only work in Chrome, which ironically perfectly illustrates the impetus for this whole undertaking. For those I use this [ungoogled Chromium](https://github.com/Eloston/ungoogled-chromium#downloads) browser, installed via homebrew.

I still have Chrome installed on my computer because I need it for some work things. We use chromedriver for some integration tests and it wasn't trivially easy to trick it into using my Chromium installation instead of looking for Chrome. Replacing chromedriver is a headache, and also not my call to make at work. But it's also not really the point for me. If Google was reduced to a browser that developers can easily launch and control programmatically, I'd be satisfied. I don't use it for anything other than running automated tests at work now.

### Google search → [DuckDuckGo](https://duckduckgo.com/)

I mostly love DuckDuckGo, but there are certain categories of searches where Google returns better results. DuckDuckGo doesn't seem to return many results from forums (like stack overflow or other stack exchange sites), which is where there the answers to a lot of my questions are, unfortunately. So I still use Google Search sometimes, but I do it in a [Firefox container](https://addons.mozilla.org/en-US/firefox/addon/multi-account-containers/) without being logged in to a Google account, which at least helps to avoid some of Google's incessant stalking.

DuckDuckGo does seem to return better results when the answer is an image, video, or regular web page, which is cool, and I suspect means it would be a perfectly fine replacement for most people. For example this blog is easier to find via DuckDuckGo than Google, which is impressive because I share a name with a small-time TV celebrity who typically dominates search results. We even look sort of similar, it's wild.

### Google Drive → [Sync](https://www.sync.com/) and [Backblaze](https://www.backblaze.com/b2/cloud-storage.html)

I use Sync to make my most used files available anywhere, and I've started moving a huge backlog of documents and notes I don't need to access often but don't want to throw away to Backblaze for longer term storage, just because it's cheaper. So far I just use their web UI to upload things in bulk and browse my files, but I'm looking into ways to do that more efficiently. I really need to de-clutter my digital life. I'm a major hoarder when it comes to digital files. I have scarcely deleted a document in the last 10 years, it's getting a bit ridiculous. But that's a goal for another year 🙂. For now I'm just putting all those files I don't really need to access but don't want to throw away in Backblaze to deal with later.

Between Sync and Backblaze I got 15GB of free storage, which is plenty for now, though as I move more and more or my scattered files over I'll have to start paying. Backblaze offers some of the cheapest object storage there is, at least.

### DNS → Cloudflare DNS

I used to use Google's DNS servers (8.8.8.8 and 8.8.4.4), now I use [Cloudflare's](https://blog.cloudflare.com/announcing-1111/) (1.1.1.1 and 1.0.0.1).

### Maps, YouTube

These ones are harder to replace. I use Apple maps now most of the time, but I still use Waze (which was acquired by Google) for directions sometimes if I'm driving somewhere. For media I mostly watch Netflix, but I also watch a lot of [TED talks](https://www.ted.com/). I used to also watch conference videos on YouTube. Now I check conference websites for those, and a lot of them have the recordings, although they're often hosted on YouTube anyway. And I also do still use YouTube sometimes, mostly for home workout videos, but also anonymously in an isolated container.

### Analytics → [Fathom](https://usefathom.com/)

It's been a while since I used Google Analytics, but I wanted to set up a basic hit counter for this blog and found Fathom. They provide simple privacy-focused analytics that work well enough for me. One really useful feature they have that I consider necessary now is being able to log analytics with a custom domain. Without this, visitors using ad-blockers don't get counted, which most estimates figure is now something like 40% of people, and probably more among tech-type people, like the ones most likely to find this blog.

I tried Cloudflare's new server-side analytics for a month, but the data didn't make as much sense (it showed the overwhelming number of visits to my homepage, even though the other analytics I had set up showed them going to one post that did well on Hacker News, which makes way more sense). Anyway, the numbers didn't seem to add up. I guess to be fair I [don't quite understand](https://twitter.com/kiraemclean/status/1340530206516387840) how Fathom's numbers add up yet either, but their support has been helpful checking it out with me to try to help me reach some interpretation that makes sense. Fathom's dashboard is really simple, at least, which I like because it's easy to understand.

### Calendar, reminders, photos, docs, video chat, news feeds → [Nextcloud](https://nextcloud.com/signup/)

For everything else I use Nextcloud now. I [run my own instance](https://kiramclean.com/blog/how-to-set-up-your-own-nextcloud-server/) of it, but there are lots of providers where you can just sign up for a simple account like anything else if you're not an insufferable nerd who enjoys maintaining servers, like me.

This is where I have another caveat to mention. I haven't migrated all of my photos off of Google Photos yet because I have about 50,000 of them and it's just a really slow process. I estimated it would take me something like 70 hours to move them all manually, so I'm looking into better ways to do it. I'm confident I can find or maybe build something easier than manually downloading and uploading 50k files in less than 70 hours.

Nextcloud does the job of automatically syncing photos from my phone, though, which is all I needed to be able to delete Google Photos from my devices. I'm not sure I'll actually stick with Nextcloud for photos in the long run. The gallery is a bit lacking. It looks like [Piwigo](https://piwigo.org/get-piwigo) might be a good alternative. Either way, I'm actually storing my photos in Backblaze, so I'll continue looking around for a different client for a bucket full of photos next year as I work on slowly culling my photos collection and migrating it to a new home.

## Total Cost

I feel like I've won a lot by doing all this. I own my data now, so Google can't [arbitrarily take it away from me](https://www.businessinsider.com/google-users-locked-out-after-years-2020-10), which gives me peace of mind. I'm no longer part of their ad ecosystem, being tracked all around the internet and having my attention sold to the highest bidder. And I also feel good about "voting with my feet", so to speak. The fewer people use Google's free products, the lower their ability to sustain their unethical business model based on selling mass surveillance data.

But I also lost some. Specifically a few hundred dollars.

Getting paid and paying for things in multiple currencies is the bane of my financial existence, but overall the final total comes out to something like CAD$400/year (roughly $300), broken down like this:

- ProtonMail - €48/year (CAD$75)
- Custom domains (for email and my cloud) - $19.74/year (CAD$25)
- Linode server (for my Nextcloud instance) - $84/year (CAD$105)
- Carbon offsets - CAD$21/year
- Fathom analytics - $140/year (CAD$180)

As you can see, $140 of the total is for Fathom analytics, which an average person probably doesn't need. Without that it comes closer to about $160, which I think is a fair price to pay for privacy, freedom, and ownership. I'm also not paying anything for storage yet, though, so I expect this to cost more next year once I finally get all my photos and archives migrated to Backblaze or whatever I end up using.

I should also emphasize it's entirely possible to switch off of Google to free alternatives. All the services I use have free tiers which are quite generous and most likely plenty for an average user. I do think people who can afford to should support independent software companies so they can run sustainable businesses off the money their customers give them and not off ad revenue, but when money is tight there are still lots of options.

## Conclusion

Once I got over the initial mental hurdle of expecting everything to be free or as cheap as possible, it seemed totally reasonable to me to pay that much for all I'm getting. What made it click for me is that all those things aren't actually free. Google sells our information and our attention to the highest bidder to generate profit for themselves, then they don't share any of it with us, despite the fact that their entire business model couldn't even exist if we all just refused to fuel it.

I know I'm just dreaming waiting for the day Google realizes the error of its ways and starts running an ethical business, but that doesn't mean it's not worth taking a stand in the meantime. Individually refusing to fuel the databases and algorithms Google profits from is one tiny thing it's totally possible to do.

***Update**: A lot of people in the comments have pointed out that it takes a lot more than this to actually escape Google's reach, which is true. My comment above that I'm "no longer part of Google's ad ecosystem" is an overstatement. As [one commenter](https://news.ycombinator.com/item?id=25654222#25655515) put it, "Google will still track much of what you do online. The internet is rotten with linked media, amp pages, blogs, and ads hosted on Google servers." They're right, but that doesn't mean none of this has any impact. I find the de-googling community can be too defeatist and purist at times, but perhaps I swing too far the other direction here. I don't want to minimize the amount of effort it takes to truly become Google-free, but I also want to encourage and inspire average people to make these changes, which requires taking a more pragmatic approach.*

**Discuss this post on [Hacker News](https://news.ycombinator.com/item?id=25654222), [Dev.to](https://dev.to/kiraemclean/what-i-use-now-instead-of-google-56lf) or [Reddit](https://www.reddit.com/r/degoogle/comments/krexp7/i_spent_2020_replacing_all_the_google_things_in/)**

### Footnotes

<small id="footnote-1-text">1. That previous link goes to a memo saying that the primary purpose of the project is "to field technology to augment or automate Processing, Exploitation, and Dissemination (PED) for tactical Unmanned Aerial System (UAS)", which means making AI for drones so they can better target humans and other "targets". That really bothers me. <a href="https://kiramclean.com/blog/what-i-use-now-instead-of-google/#footnote-1">↵</a></small>
