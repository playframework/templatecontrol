These are scripts you can use to create your forks and branches for the Play and/or Lagom repos.

(Note: Template Control for Lagom is work in progress. Currently we only have the scripts to create branches and forks.)

## Fork the template repos

To fork every Play or Lagom template repo invoke:

```bash
./create-forks-play.sh

./create-forks-lagom.sh
```

The list of templates that it will fork is declared in `templates-play.sh` and `templates-lagom.sh`.

## Branch the template repos

To create branches for ever Play or Lagom template repo invoke:

```bash
./create-branches-play.sh 2.7.x

./create-branches-lagom.sh 1.5.x
```

This will create a `2.7.x` branch on every Play template repo declared in `templates-play.sh` and a `1.5.x` branch on every Lagom template in `templates-lagom.sh`.

This is especially useful when we have a new major release.  Make sure you add this new major release in the
relevant configuration.
