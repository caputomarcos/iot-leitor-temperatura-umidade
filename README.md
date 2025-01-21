

When loaded, create 8 ttys interconnected:

/dev/tnt0 <=> /dev/tnt1
/dev/tnt2 <=> /dev/tnt3
/dev/tnt4 <=> /dev/tnt5
/dev/tnt6 <=> /dev/tnt7

the connection is:

TX -> RX
RX <- TX
RTS -> CTS
CTS <- RTS
DSR <- DTR
CD <- DTR
DTR -> DSR
DTR -> CD


```
○ → socat -d -d pty,raw,echo=0 pty,raw,echo=0
2025/01/20 23:05:50 socat[3267235] N PTY is /dev/pts/3
2025/01/20 23:05:50 socat[3267235] N PTY is /dev/pts/4
2025/01/20 23:05:50 socat[3267235] N starting data transfer loop with FDs [5,5] and [7,7]
```


```
± |main ✓| → sudo socat -d -d /dev/tnt0 /dev/pts/3
2025/01/20 23:07:27 socat[3269668] N opening character device "/dev/tnt0" for reading and writing
2025/01/20 23:07:27 socat[3269668] N opening character device "/dev/pts/3" for reading and writing
2025/01/20 23:07:27 socat[3269668] N starting data transfer loop with FDs [5,5] and [6,6]
```



```
sudo echo "TEMP:25.5" > /dev/tnt0
```

![image](https://github.com/user-attachments/assets/6c5fee2a-e0a7-41cd-a71a-e820611046b6)

![image](https://github.com/user-attachments/assets/a280e3df-4606-4f97-9c90-5024c2c85d3d)

![image](https://github.com/user-attachments/assets/85d13fb6-f3e2-48e8-8f2d-9892a5b761fc)


https://reports.cucumber.io/reports/76b4498a-43f6-4563-aa6c-a8e934fb3d3a










