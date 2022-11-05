# MinecraftChatAlternative

## What is this?

This is a fabric mod for Minecraft that replaced the original in-game chat. This is a demonstration of what we can do to f??k Microsoft in adding insecure censorship into our beloved game Minecraft. This is my first Minecraft mod ever, it's NOT a user-friendly mod. But if you're a developer seeking for ideas or solutions, you're welcome to use the code.

I treat this as an ultimate solution to the message signature. Mods like [NoChatReports](https://github.com/Aizistral-Studios/No-Chat-Reports) make sure you won't get banned by striping all signatures from your message. However, if a server enforce those signature, you will be disconnected because your message don't have valid signature, thus, you have to choose, safe or play? (Technically, you can safely play those server as long as you never send any message. But how?)

With this mod, you can fully eliminate the need of Minecraft's original chat.

## So, what does this mod do?

In vanilla Minecraft, the flow or your message looks like this:

1. You type your message and hit enter.
2. Your minecraft client signed the message and send it to server.
3. The server verify your message's signature.
4. Server forward your message along with the signature and sender info to each client.
5. Someone's client verify the message and signature.
6. Someone's client show the message on the screen.

(Here I strongly recommend you some videos from [Aizistral](https://www.youtube.com/c/Aizistral), where he detailed explained WTF is message signature & chat report, where is the flaw, how someone can abuse it, and how to protect yourself.)

While Aizistral's NoChatReports(I'll use NCR for short) remove the signature, this mod hijack the client to not send your message to the server (of course, signature free).

With this mod, the flow of your message will be:

1. You type your message and hit enter.
2. The mod intercept the message.
3. The mod tries to send message to each player in the server.
4. The mod in someone's client got your message.
5. The mod pack this message as system message (the message don't need signature).
6. The mod add this message to chat. (Aka show the message).

You might notice that there is no server involved, and, Yes, this is a P2P system. It comes with all advantages of P2P system, like decentralized, privacy, secure, etc. While it also got all disadvantages of P2P system, like peer might not able to connect, etc.

## OK, then how can I try it?

Like I said, this is a demo instead of a user-friendly mod. You will get some issue when using this mod. Most issue can be solved by googling things. For the rest of them, [submit an issue](https://github.com/hurui200320/MinecraftChatAlternative/issues).

For this mod to work, you need:

+ A running [I2P](https://geti2p.net/) instance.
  + The mode is not matter, in some area, I2P will run as hidden mode by default to protect users. That's ok, as long as you have active peers to access the I2P network.
+ Minecraft 1.19.2 with fabric loader.
+ Two Mojang or Microsoft account. (Only one is needed if you have friends)
+ This mod.

It's recommended to run the I2P instance for a while so your instance can have a better integration over the network. Otherwise, your client can't reach others.

A proper account is required since only online clients will get keypair from mojang. Offline clients won't work. However, you can join to offline server.

Normally, this mod will automatically find your I2P instance, if you keep I2CP settings untouched.

Then join a server. By default, yourself is the only one can receive the message. The mod will notice you by putting message like "Failed to send message to `<playerName>`".

To connect with others:

+ Type "!expose" in chat and hit enter, the mod will send a signed message containing your i2p address to the server. Others will get this and try to connect to you.
+ Or, type "!gen" command, it will generate a connect command and copy to your clipboard, so you can send this command to others. They can paste the command and the mod will try to connect to you.

Either way, you need to know other's i2p address, or they need to know yours. Once a connection is made, the reverse connection will be made automatically.

Also, with peer exchange, if there are already players connected to each other, you only need manually connect one of them, and then they will connect to you. In the end, there will be NxN connections, each player needs connections with rest of the players.

Now you can start typing and sending message safely. You won't see any log on serverside since they don't know what you send. And I2P is end-to-end encryption, only you and the receiver knows the message.

In case you want to send private message, you can click someone's message, the command suggestion will be `!msg i2p <playerName> `, followed by your message. This command will let mod send message to the player you specified. Others will never know what you said.

## Cool, but how it works?

I'm glad to ask (or didn't, it just me imaging someone would ask). This mod is not limited to I2P. With code that hijack the chat system, you can route the message to anywhere you want: Matrix/Element, discord, telegram group, irc, you name it. But for I2P part, it's a P2P system designed by myself. I have no such experience before, and this might not be the best design, but at least it works.

Each client has a peer. A peer in P2P system is both client and server. Let's get two players call Alice and Bob. And the request was sent as json string in one line (the `MCARequest` class), and the response a line of string, but might be json or plain string. 

At the beginning, they will both make a self-connection so that they show their own messages. Just like the original in-game chat: if server lags, you won't see your message until server send that to you.

Let's say Alice want to connect Bob. Bob give his i2p address to Alice.
1. Alice's client make a connection to Bob's server. The Bob's server don't know who it is.
2. Alice send a `Auth` request. This request contains Alice's profile UUID, public key, username, i2p address, and the signature of username and i2p address. Note: this signature is not message signature, players cannot report you based on that. This signature is needed to make your Alice is real Alice, not pretended by someone.
3. Bob verify the auth request, added to a list called `knownIds`, where records all valid auth request, so they can send to others when someone ask for peer exchanges. If the auth failed, Bob's server will disconnect immediately.
   + Now Alice only knows about herself, while Blob knows himself and Alice.
4. Alice send an exchange request, sending her `knownIds` list to Bob.
5. Bob add those list in `newPeerQueue`, and send back his `knownIds` list to Alice.
6. Alice add those list in `newPeerQueue`.
   + Now there is only a connection from Alice to Bob. Alice can send messages to Bob, but Bob don't know who it is (Bob need search his outgoing connections to know the username, to prevent income-only spam). Also, Bob can't send message to Alice since Bob's client didn't connect Alice's server.
7. Now both Alice and Bob start processing the `newPeerQueue`.
   + At this time, they will both try to connect others. For Alice, she is already connected to Bob, so nothing will happen. For Bob, he will try to connect Alice. If success, then now we have connections both from Alice/Bob to Bob/Alice.

At this point, both Alice and Bob can send message to each other.

If Alice want to connect with a cluster of peers, like Bob and other players already have interconnections, Alice want to join them. All Alice need to do is connect to Bob. By exchanging peers, Bob will send all other member to Alice so that she can connect them one by one.

## Then, what's next?

You might notice, exchanging I2P address (finding peers) is manual. What if we can automate this progress? In BitTorrent worlds, there are trackers to help BitTorrent clients to find other clients, can we use that?

The answer is technically yes but I tried and no. In theory, you can announce your peer info to trackers with a specific info hash. If all peers using the same info hash, they can find each other. And if some normal BT client find us, they won't get what they expected, so they disconnect us.

I do implement those code for trackers, but seems like the tracker randomly take my announcement. Most time it ignores that, and sometime it take my announcement. Since I have no idea why, so I'll just leave it there.

And as I said before, this is a demo, which means like my other projects, there will not be active development anymore, but unlike other projects, I don't archive this project for now. So if you're a developer, feel free to join or fork :)
