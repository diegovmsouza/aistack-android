#!/usr/bin/env bash
# Desliga a morte por pressão de memória do systemd-oomd nas sessões de usuário
# (padrão do Ubuntu: matar o app de maior reclaim quando user@UID passa de 50% por 20 s).
# O OOM killer do kernel continua valendo quando a memória e a swap acabam de fato.
# Desfazer: sudo rm /etc/systemd/system/user@.service.d/90-oomd-permissivo.conf && sudo systemctl daemon-reload
set -euo pipefail
U="user@$(id -u).service"
sudo install -d /etc/systemd/system/user@.service.d
sudo tee /etc/systemd/system/user@.service.d/90-oomd-permissivo.conf >/dev/null <<'CONF'
[Service]
ManagedOOMMemoryPressure=auto
CONF
sudo systemctl daemon-reload
sudo systemctl set-property --runtime "$U" ManagedOOMMemoryPressure=auto
systemctl show "$U" -p ManagedOOMMemoryPressure
if oomctl | sed -n '/Memory Pressure Monitored/,$p' | grep -q "$U"; then
  echo "ATENÇÃO: o oomd ainda monitora $U"; exit 1
else
  echo "OK: o oomd não mata mais nada em $U por pressão de memória"
fi
