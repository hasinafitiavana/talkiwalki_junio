# Avancement

## Étape 3A — réseau local Host/Client

Terminé : un Host ouvre un serveur TCP local sur le port `49321`, et un Client
rejoint manuellement l'adresse IPv4 du Host (le port peut être ajouté sous la
forme `adresse:port`). Le transport utilise un protocole texte UTF-8 délimité
par ligne, versionné et validé. La liste des participants est distribuée à
tous les clients après chaque arrivée ou départ.

Hors périmètre à ce stade : découverte NSD/mDNS, audio/PTT réel, microphone,
codec, service au premier plan et connexion par hotspot.

## Étape 3B — découverte automatique LAN

Terminé : le Host annonce son groupe via Android NSD/DNS-SD avec le type
`_talkiwalki._tcp.`. L'écran de jonction lance la recherche, résout les services
trouvés, élimine les doublons et retire les services perdus. Une adresse IP peut
toujours être saisie manuellement lorsque le multicast est filtré par le réseau.

Correction NSD/mDNS : la découverte utilise désormais un `MulticastLock` Wi-Fi
uniquement pendant la recherche, et les résolutions sont sérialisées. Cette
correction cible les appareils/routeurs où les paquets mDNS sont filtrés sans
verrou ou où des résolutions parallèles échouent. Les diagnostics sont visibles
avec le tag Logcat `TalkiWalkiNSD`.
