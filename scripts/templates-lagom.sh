#!/bin/bash

##  Keep the templates list in sync with ./src/main/scala/model/Lagom.scala !

declare -a lagom_templates=( 
   "online-auction-scala"
   "online-auction-java"
   "lagom-java-sbt-chirper-example"
   "lagom-java-maven-chirper-example"
   "lagom-scala.g8"
   "lagom-java.g8"
   "lagom-scala-openshift-smoketests"
   "lagom-java-openshift-smoketests"
   "lagom-scala-grpc-example"
   "lagom-java-grpc-example"
   "shopping-cart-scala"
   "shopping-cart-java"
  )
  
lagom_github=git@github.com:lagom