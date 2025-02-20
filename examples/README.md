# Examples

This submodule shows how one can work with James. 

Each subprojects illustrate a specific concept.

## How to customize mail processing

At the heart of James lies the Mailet container, which allows mail processing. This is splitted into smaller units, with specific responsibilities:

 - `Mailets`: Are operations performed with the mail: modifying it, performing a side-effect, etc...
 - `Matchers`: Are per-recipient conditions for mailet executions
 - `Processors`: Are matcher/mailet pair execution threads

Once we define the mailet container content through the mailetcontailer.xml file. Hence, we can arrange James standard 
components to achieve basic logic. But what if our goals are more complex? What if we need our own processing components?

[This example](custom-mailets) shows how to write such components!

## Configure Custom Mailbox Listeners

Mailbox Listener is a component in James Mailbox System. Each time an action is applied on a mailbox(adding, deleting),
 or on an email(adding, deleting, updating flags...), then an event representing that action is generated and delivered 
 to all the Listeners that had been registered before. After receiving events, listeners retrieve information from the 
 events then execute their business (Indexing emails in ElasticSearch, updating quota of users, detecting spam emails...)
 
**Mailbox Listeners** allow customizing the behaviour of James used as a Mail Delivery Agent (MDA). 

[This example](custom-listeners) shows how to write such components!

## Configure Custom SMTP hooks

SMTP hooks allow integrating third party systems with the SMTP stack, allows writing additional SMTP extensions, for 
instance. 

[This example](custom-smtp-hooks) shows how to write such components!

## Configure Custom SMTP commands

This subproject demonstrates how to write custom commands for Apache James SMTP server. 

[This example](custom-smtp-command) shows how to write additional SMTP commands!

## Configure Custom WebAdmin routes

The current project demonstrates how to write custom webadmin routes for Apache James. This enables writing new 
administrative features exposed over a REST API. This can allow you to write some additional features, make James 
interact with third party systems, do advance reporting... 

[This example](custom-webadmin-route) shows how to write additional Webadmin routes!

## Write Custom James server assembly

[This example](custom-james-assembly) demonstrates how to write a custom assembly in order to write your own tailor-made server.
               
This enables:
               
 - Arbitrary composition of technologies (example JPA mailbox with Cassandra user management)
 - Write any additional components
 - Drop any unneeded component
 - You have control on the dependencies and can reduce the classpath size
