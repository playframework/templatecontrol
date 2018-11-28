
This folder contains scripts that you can use to create your forks and branches for Play and/or Lagom.

(Note: Template Control for Lagom is work in progress. Currently we only have the scripts to create branches and forks.)

## Creating forks

To create forks for a Play and Lagom, you can call: 
```bash
./create-forks-play.sh 2.7.x

./create-forks-lagom.sh 1.5.x
```

This will create forks for each Play template declared in `templates-play.sh` and for each Lagom template in `templates-lagom.sh`.


## Creating branches
To create branches for a Play and Lagom, you can call: 

```bash
./create-branches-play.sh 2.7.x

./create-branches-lagom.sh 1.5.x
```

This will create `2.7.x` branches on each Play template declared in `templates-play.sh` and `1.5.x` for each Lagom template in `templates-lagom.sh`.

This is especially useful when we have a new major release. 