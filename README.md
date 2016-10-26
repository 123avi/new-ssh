**Async ssh**

**Goal**

execute shell commands on ssh server asynchronously using akka actors 

**Overview**

* using apache mina project http://mina.apache.org/sshd-project/
* Connect to ssh server and execute shell commands
* open session
* each session can open several channels and run commands 
* each channel read state is managed by akka actor 
* in order to run tests you need to create application.conf 

```xml
test{
  server{
    address = "10.0.0.151"
    port = 22
    user = foo
    password = bar
  }
}
```
*free ssh accounts for testing : http://serverfault.com/a/185155
 I used for testing http://sdf.org/*

questions/suggestions -> 123avi@gmail.com

**pull requests /contributions are very welcome**
