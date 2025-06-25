@Composable
private fun NetworkPhoto(
    photoUri: Uri?,
    onPhotoClick: () -> Unit
) {
    photoUri?.let { uri ->
        var isHovered by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { onPhotoClick() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHovered = true
                            tryAwaitRelease()
                            isHovered = false
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isHovered) 8.dp else 4.dp
            )
        ) {
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.network_photo),
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(if (isHovered) 1.05f else 1f),
                    contentScale = ContentScale.Crop,
                    onLoading = {
                        // Show loading placeholder
                    },
                    onError = {
                        // Show error placeholder
                    }
                )
                
                // Enhanced overlay with smooth transitions
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "View full image",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                // Photo indicator badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}