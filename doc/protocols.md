# Protocols

## Message Flows in Reference Implementation

### Runtime Platform to Agent Container

* **message**: HTTP Request to CA (in HTTP handler thread), send message via `agent_ref tell`
* **broadcast**: HTTP Request to CA (in HTTP handler thread), send message via `broker.publish`
* **invoke**: HTTP Request to CA (in HTTP handler thread), send `Invoke` to agent via `ask invoke` protocol, wait for response (in CA thread), reply in HTTP response

![RP-CA Interactions](img/messages-rp-ca.png)

### Agent Container to Runtime Platform

* **message**: agent sends HTTP Request to RP via helper method in super class
* **broadcast**: agent sends HTTP Request to RP via helper method in super class
* **invoke**: agent sends HTTP Request to RP via helper method in super class, waits for response in its own thread

![CA-RP Interactions](img/messages-ca-rp.png)

### Within Agent Container

* **message**: using regular JIAC-VI `agent_ref tell`
* **broadcast**: using regular JIAC-VI `broker publish`
* **invoke**: using regular JIAC-VI `ask invoke`, either with JIAC++ `Invoke` object or any other payload

![Internal Interactions](img/messages-internal.png)

### Runtime Platform to Runtime Platform 

* not yet implemented, but basically, the same flow for all communication
* look up other connected RP that has the target container
* forward **message** or **invoke** to that platform
* unclear: **broadcast**: how to prevent endlessly forwarding message back and forth? keep track of recently seen messages? or keep sender-history in each forwarded message and don't forward messages to RP in that history?

## Protocol for connecting two Runtime Platforms

* platform A receives request to connect to platform B
* sends request to connect to platform A (itself) to platform B, adds B to "pending"
* platform B does the same, sending another request back to A and adding A to "pending"
* platform A recognizes the new request as already being processed and replies immediately
* platform B replies to original request, both platforms call "info" on each other

![Platform Connection Protocol](img/connect-platform.png)

## Protocol for notifying about updated Containers or connected Platforms

* container/platform calls `/containers/notify` or `/connections/notify` with own ID/URL respectively
* the idea behind "notify, then pull info" instead of "push info" is to make sure that the info does actually come from the container/platform in question and not someone else
* receiving platform calls `/info` for that container/platform, stores updated information
* return `true` if update successful, `false` if not reachable (see below) and 404 if unknown/not in list
* if container/platform are not reachable, their information is removed from the platform
* can be called if container's agents/actions change, if container is about to die, or at any time by the user
* update in containers (via add/remove or update) automatically triggers notification of connected platforms

![Notify/Update Protocol](img/notify-update.png)