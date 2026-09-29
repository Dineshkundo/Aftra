# Coda_VPNGateway: Upgrade from VpnGw1 to VpnGw1AZ

| Item | Value |
|---|---|
| Subscription | CODA - DEV / UAT (`d629b553-466f-4caa-b64b-9ba2bae97c3f`) |
| Resource group | `CODA_RG` |
| Gateway | `Coda_VPNGateway` (East US) |
| Azure notice | Service Health tracking ID `DYN8-G00` |
| Change date | 2026-09-29 |
| Result | SKU changed from `VpnGw1` to `VpnGw1AZ`, tunnel `Connected`, public IPs unchanged |

## 1. Why the change was needed

Microsoft is retiring the non-AZ VPN Gateway SKUs (VpnGw1-5) on **30 September 2026**. They are replaced by the availability-zone SKUs (VpnGw1AZ-5AZ).

Reasons given by Microsoft: the old SKUs lack redundancy, have lower availability, and can cost more because of the extra failover setup they need.

If nothing is done:

- The gateway might keep working, but reliability, availability and support are **not guaranteed**.
- The VPN Gateway SLA no longer covers gateways still on the legacy platform.
- Microsoft may migrate the gateway itself, which can cause an unplanned brief interruption.
- Non-AZ gateways no longer accept configuration changes. Any update returns a validation error until the SKU is upgraded.

## 2. What this gateway is used for

- **Site-to-site tunnel** `ASA_FTD-Azure` to the on-premises Cisco firewall (local network gateway `Coda_LocalNetworkGateway`, remote IP `38.79.64.66`).
- Serves **DevVnet**, which holds the dev/UAT VMs: Jenkins-BuildServer, Boomi-Integration, Matching-Service (and QA backup), RedhatServerUAT, RHELDevQa, SecurityScanner.
- Metrics showed daily traffic for the last 30 days, so the gateway is in active use and could not simply be deleted.
- **Point-to-site** was configured (SSTP, certificate authentication, pool `192.168.1.0/24`), but its root certificate had expired, so P2S clients could not connect.

Gateway configuration at the time: VpnGw1, Generation 1, active-active, BGP off, three Standard static public IPs.

## 3. State before the change

| Item | Value |
|---|---|
| SKU | `VpnGw1` (non-AZ) |
| Public IP (site-to-site #1) | `172.173.211.221` (resource `172.174.30.103`) |
| Public IP (site-to-site #2) | `172.173.212.21` (resource `172.174.30.104`) |
| Public IP (P2S) | `20.168.244.196` (resource `P2S-Public-IP`) |
| Public IP SKU | Standard, Static, non-zonal |
| P2S root certificate | `Azure-Root-Certificate`, **expired** |

The portal "Migrate to Standard IP" tab reported *No migration necessary*. That tool only handles Basic to Standard public IP migration and does not cover this case. The correct action was a direct SKU upgrade.

## 4. Upgrade path used

Microsoft supports a direct upgrade from VpnGw1 (Gen1) to VpnGw1AZ. Per Microsoft's documentation:

- Public IP addresses do not change.
- The on-premises VPN device and P2S clients do not need reconfiguring.
- Same-tier upgrades to an AZ SKU have no downtime. The operation takes about 45 minutes.

## 5. Problems hit and how they were solved

| # | Error | Cause | Fix |
|---|---|---|---|
| 1 | `VpnClientRootCertificateExpired` | The expired P2S root certificate blocked all gateway updates | Replace the certificate (see below) |
| 2 | `VpnClientConfigurationCertificateAuthParamVpnClientRootCertificatesIsNotSpecified` | P2S certificate auth was on and no root certificate remained | A valid root certificate must exist in the same request |
| 3 | Portal: `Data for certificate ... Azure-Root-Certificate is invalid` | The portal choked on the expired certificate | Use the CLI instead of the portal |
| 4 | CLI `Missing a required field ... publicCertData` | `--set` shortcut did not match the nested certificate structure | Use `az resource update` with ARM JSON |
| 5 | `Could not find member 'sku'` | In the ARM schema, `sku` sits under `properties` | Use `properties.sku.name` |

## 6. Commands used

Generate a replacement root certificate (placeholder, see section 8):

```bash
openssl req -x509 -newkey rsa:2048 -nodes -keyout /tmp/coda-root.key -out /tmp/coda-root.pem \
  -days 1825 -subj "/CN=Coda-P2S-Root-2026"
CERT=$(openssl x509 -in /tmp/coda-root.pem -outform der | base64 -w0)
```

Replace the certificate and upgrade the SKU in one request:

```bash
GWID=$(az network vnet-gateway show -g CODA_RG -n Coda_VPNGateway --query id -o tsv)

az resource update --ids "$GWID" --api-version 2024-05-01 \
  --set properties.sku.name=VpnGw1AZ properties.sku.tier=VpnGw1AZ \
  properties.vpnClientConfiguration.vpnClientRootCertificates="[{\"name\":\"Coda-P2S-Root-2026\",\"properties\":{\"publicCertData\":\"$CERT\"}}]"
```

## 7. Verification (all passed)

```bash
az network vnet-gateway show -g CODA_RG -n Coda_VPNGateway \
  --query "{sku:sku.name, state:provisioningState, certs:vpnClientConfiguration.vpnClientRootCertificates[].name}" -o json
az network vpn-connection show -g CODA_RG -n ASA_FTD-Azure --query connectionStatus -o tsv
```

| Check | Result |
|---|---|
| SKU | `VpnGw1AZ` |
| Provisioning state | `Succeeded` |
| Root certificates | Only `Coda-P2S-Root-2026` |
| `ASA_FTD-Azure` connection | `Connected` |
| Public IPs | Unchanged (`172.173.211.221`, `172.173.212.21`, `20.168.244.196`) |

Recommended follow-up test: from Jenkins, reach a host on the on-premises network to confirm traffic flows end to end.

## 8. Notes and open items

- **Placeholder root certificate:** `Coda-P2S-Root-2026` was created only to satisfy the gateway's requirement to have a valid root when certificate authentication is on. No client certificates have been issued from it, so P2S remains effectively disabled. If P2S is not needed, delete the private key (`shred -u /tmp/coda-root.key`). If P2S is needed, store the key securely and issue client certificates from it.
- **SSTP retirement:** SSTP stops working on **31 March 2027**, and the portal shows a banner for this. Decide whether P2S is still needed. If yes, switch the tunnel type to IKEv2 (or OpenVPN) and issue new client certificates and profiles before that date. If no, disable P2S.
- **Templates and scripts:** update any that reference `VpnGw1` to `VpnGw1AZ`.
- **Cost:** AZ SKU pricing differs. Check Cost Management next month.
- **Root cause of the old certificate:** who created `Azure-Root-Certificate` and where its private key is stored is unknown. Ask the team that originally set up the gateway.

## 9. Rollback

Not recommended, since non-AZ SKUs are retired. Downgrading within the AZ family is supported, but returning to a non-AZ SKU is not.
