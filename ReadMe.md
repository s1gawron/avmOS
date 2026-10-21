# Abstract Virtual Machine Operating System

### This project was inspired by [Scott Hanselman TinyOS](https://github.com/shanselman/TinyOS) final project for Operating Systems classes.

For this project I got the idea by looking at GitHub profiles of people I follow. Looking at TinyOS by Scott I thought it would be cool, interesting and very useful to learn how operating systems work.

This project should allow me to better understand the software that I am writing. The work I will be doing here will not be a copy-paste of Scott's project, but I will try to get as much knowledge about operating systems as I can.
I will start my research in [Gynvael's book "Zrozumieć programowanie"](https://github.com/gynvael) (unluckily this book is only in Polish) in which he describes a lot of interesting Computer Science related topics and then move on to other sources where I can grab some knowledge.

The requirements for this project are either in this repository [Scotts_OriginalAssignment.md](Scotts_OriginalAssignment.md) as a backup or in Scott's TinyOS repository.

### How to run this project?

Requires JDK 25 and Maven.

Build (compiles, runs the tests and produces `target/avmOS.jar`):

```
mvn package
```

Run:

```
java -jar target/avmOS.jar <memory bytes> <program1.txt> <program2.txt> ...
java -jar target/avmOS.jar 1024 src/main/resources/sample_programs/scott01.txt
```

Show the machine code of a program (offset, bytes, instruction) without running it:

```
java -jar target/avmOS.jar --dump 1024 src/main/resources/sample_programs/idle.txt
```