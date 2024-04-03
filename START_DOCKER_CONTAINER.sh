#!/bin/bash

docker run --rm -it -v "`pwd`/data:/workspace/data" ldap2moodle:0.0.1-SNAPSHOT 
