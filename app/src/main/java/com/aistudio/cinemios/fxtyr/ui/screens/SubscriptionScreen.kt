package com.aistudio.cinemios.fxtyr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.subscription.PremiumFeature
import com.aistudio.cinemios.fxtyr.subscription.SubscriptionPlan
import com.aistudio.cinemios.fxtyr.subscription.SubscriptionState
import com.aistudio.cinemios.fxtyr.subscription.formatSubscriptionDate
import com.aistudio.cinemios.fxtyr.ui.components.seeplus.SeePlusColors
import com.aistudio.cinemios.fxtyr.ui.components.seeplus.SeePlusWordmark
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteSuccess
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SubscriptionScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subscription by viewModel.subscriptionState.collectAsState()
    val context = LocalContext.current

    var selectedPlanKey by rememberSaveable { mutableStateOf(SubscriptionPlan.YEARLY.storageKey) }
    val selectedPlan = SubscriptionPlan.fromKey(selectedPlanKey) ?: SubscriptionPlan.YEARLY

    var showPaymentSheet by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshSubscription()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubscriptionTopBar(onBackClick = onBackClick)

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = 8.dp,
                        bottom = if (subscription.isActive) 28.dp else 116.dp
                    )
            ) {
                SeePlusHero(
                    isPremium = subscription.isActive,
                    planTitle = subscription.plan?.title,
                    expiryLabel = formatSubscriptionDate(subscription.expiresAt),
                    daysRemaining = subscription.daysRemaining()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (subscription.isActive) {
                    SectionTitle(text = "تفاصيل العضوية")
                    Spacer(modifier = Modifier.height(10.dp))
                    MembershipDetailsCard(
                        subscription = subscription,
                        onAutoRenewChange = { viewModel.setSubscriptionAutoRenew(it) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle(text = "مزاياك في seePlus")
                    Spacer(modifier = Modifier.height(10.dp))
                    PremiumBenefitsCard()
                    Spacer(modifier = Modifier.height(24.dp))
                    CancelSubscriptionRow(onClick = { showCancelDialog = true })
                    Spacer(modifier = Modifier.height(20.dp))
                    TermsFooter()
                } else {
                    SectionTitle(text = "لماذا seePlus؟")
                    Spacer(modifier = Modifier.height(10.dp))
                    PremiumBenefitsCard()
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle(text = "اختر خطتك")
                    Spacer(modifier = Modifier.height(10.dp))
                    PlanCard(
                        plan = SubscriptionPlan.MONTHLY,
                        selected = selectedPlan == SubscriptionPlan.MONTHLY,
                        onClick = { selectedPlanKey = SubscriptionPlan.MONTHLY.storageKey }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PlanCard(
                        plan = SubscriptionPlan.YEARLY,
                        selected = selectedPlan == SubscriptionPlan.YEARLY,
                        onClick = { selectedPlanKey = SubscriptionPlan.YEARLY.storageKey }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    TermsFooter()
                }
            }

            if (!subscription.isActive) {
                SubscribeBottomBar(
                    plan = selectedPlan,
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (showPaymentSheet) {
        PaymentSheet(
            plan = selectedPlan,
            onDismiss = { showPaymentSheet = false },
            onConfirm = { pin -> viewModel.activateSubscription(selectedPlan, pin) },
            onSuccess = {
                showPaymentSheet = false
                showSuccessDialog = true
            }
        )
    }

    if (showSuccessDialog) {
        PaymentSuccessDialog(
            plan = subscription.plan ?: selectedPlan,
            onDismiss = { showSuccessDialog = false }
        )
    }

    if (showCancelDialog) {
        CancelSubscriptionDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                viewModel.cancelSubscription()
                showCancelDialog = false
                Toast.makeText(
                    context,
                    "تم إلغاء اشتراك seePlus",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun SubscriptionTopBar(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(44.dp)
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        SeePlusWordmark(
            modifier = Modifier.align(Alignment.Center),
            fontSize = 22.sp
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = SeePlusColors.DeepGold,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "PREMIUM",
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = SeePlusColors.DeepGold
            )
        }
    }
}

@Composable
private fun SeePlusHero(
    isPremium: Boolean,
    planTitle: String?,
    expiryLabel: String,
    daysRemaining: Int
) {
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SeePlusColors.EspressoSoft,
                        SeePlusColors.Espresso,
                        Color(0xFF3A2A1C)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = SeePlusColors.Gold.copy(alpha = 0.35f),
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-100).dp)
                .size(230.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SeePlusColors.Gold.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = SeePlusColors.Gold.copy(alpha = 0.07f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 30.dp)
                .size(160.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = SeePlusColors.Gold.copy(alpha = 0.14f),
                    contentColor = SeePlusColors.Gold,
                    border = BorderStroke(1.dp, SeePlusColors.Gold.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isPremium) "عضوية seePlus" else "seePlus PREMIUM",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                if (isPremium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PaletteSuccess)
                        )
                        Text(
                            text = "نشط",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaletteSuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (isPremium) "مرحبًا بك في عالم seePlus" else "سينما بلا حدود.",
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                color = SeePlusColors.Cream
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = if (isPremium && planTitle != null) {
                    "خطة $planTitle سارية حتى $expiryLabel • متبقي $daysRemaining يومًا"
                } else {
                    "استمتع بأفضل الأفلام والمسلسلات بجودة استثنائية، وتحميل بلا قيود، وتجربة مصممة لك بالكامل."
                },
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = SeePlusColors.Cream.copy(alpha = 0.65f)
            )

            if (!isPremium) {
                Spacer(modifier = Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = SeePlusColors.Gold,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "4.9 • أكثر من 120 ألف عضو",
                        fontSize = 10.sp,
                        color = SeePlusColors.Cream.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun PremiumBenefitsCard() {
    val features = PremiumFeature.all

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            features.forEachIndexed { index, feature ->
                BenefitRow(feature = feature)
                if (index != features.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp, end = 14.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(feature: PremiumFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = featureIcon(feature),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = feature.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = feature.description,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
            )
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun featureIcon(feature: PremiumFeature): ImageVector = when (feature) {
    PremiumFeature.OFFLINE_DOWNLOADS -> Icons.Default.Download
    PremiumFeature.EXCLUSIVE_SUBTITLES -> Icons.Default.ClosedCaption
    PremiumFeature.HIGH_QUALITY_PLAYBACK -> Icons.Default.Star
    PremiumFeature.WATCHLIST_SYNC -> Icons.Default.Refresh
    else -> Icons.Default.Star
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (selected) {
        SeePlusColors.DeepGold
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        shape = shape,
        color = if (selected) {
            SeePlusColors.Gold.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = if (selected) SeePlusColors.DeepGold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(SeePlusColors.DeepGold)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = plan.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (plan.savingsLabel != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = plan.savingsLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.priceLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = " / ${plan.periodLabel}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = plan.monthlyEquivalent,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun SubscribeBottomBar(
    plan: SubscriptionPlan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeePlusColors.Gold,
                    contentColor = SeePlusColors.Espresso
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "اشترك في seePlus",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${plan.priceLabel}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "دفع آمن • إلغاء في أي وقت",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun MembershipDetailsCard(
    subscription: SubscriptionState,
    onAutoRenewChange: (Boolean) -> Unit
) {
    val plan = subscription.plan ?: SubscriptionPlan.YEARLY

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeePlusWordmark(fontSize = 19.sp)
                Surface(
                    shape = CircleShape,
                    color = PaletteSuccess.copy(alpha = 0.14f),
                    contentColor = PaletteSuccess
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(PaletteSuccess)
                        )
                        Text(
                            text = "نشط",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )

            DetailRow(label = "الخطة الحالية", value = "seePlus ${plan.title}")
            DetailRow(label = "تاريخ البداية", value = formatSubscriptionDate(subscription.startedAt))
            DetailRow(
                label = if (subscription.autoRenew) "التجديد التالي" else "ينتهي في",
                value = formatSubscriptionDate(subscription.expiresAt)
            )
            DetailRow(label = "المبلغ", value = "${plan.priceLabel} / ${plan.periodLabel}")
            DetailRow(
                label = "رقم العملية",
                value = subscription.transactionId.ifBlank { "—" },
                valueFontFamily = JetBrainsMonoFontFamily
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "التجديد التلقائي",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (subscription.autoRenew) {
                            "سيتم التجديد تلقائيًا في ${formatSubscriptionDate(subscription.expiresAt)}"
                        } else {
                            "لن يتم تجديد الاشتراك تلقائيًا"
                        },
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
                Switch(
                    checked = subscription.autoRenew,
                    onCheckedChange = onAutoRenewChange
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueFontFamily: FontFamily? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontSize = if (valueFontFamily != null) 9.5.sp else 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = valueFontFamily,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CancelSubscriptionRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = PaletteMutedRed.copy(alpha = 0.18f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = PaletteMutedRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "إلغاء الاشتراك",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PaletteMutedRed
            )
        }
    }
}

@Composable
private fun TermsFooter() {
    Text(
        text = "بالاشتراك في seePlus، أنت توافق على شروط الاستخدام وسياسة الخصوصية. الأسعار تشمل ضريبة القيمة المضافة.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        textAlign = TextAlign.Center,
        fontSize = 9.sp,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(
    plan: SubscriptionPlan,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var pin by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "تأكيد الاشتراك",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "خطوة واحدة تفصلك عن seePlus",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                SeePlusWordmark(fontSize = 18.sp)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                tonalElevation = 0.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "خطة seePlus ${plan.title}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = plan.billingDescription,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = plan.priceLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "شامل الضريبة",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "طريقة الدفع",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "seePlus Pay • آمن",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الرمز السري",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "4 أرقام",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }

                PinInput(
                    pin = pin,
                    onPinChange = {
                        pin = it
                        errorMessage = null
                    },
                    isError = errorMessage != null,
                    enabled = !isProcessing
                )

                Text(
                    text = errorMessage ?: "أدخل الرمز السري لتأكيد عملية الاشتراك.",
                    fontSize = 10.sp,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Button(
                onClick = {
                    keyboardController?.hide()
                    errorMessage = null
                    isProcessing = true
                    scope.launch {
                        delay(900)
                        val success = onConfirm(pin)
                        isProcessing = false
                        if (success) {
                            onSuccess()
                        } else {
                            pin = ""
                            errorMessage = "الرمز السري غير صحيح. يرجى المحاولة مرة أخرى."
                        }
                    }
                },
                enabled = pin.length == 4 && !isProcessing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeePlusColors.Gold,
                    contentColor = SeePlusColors.Espresso
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SeePlusColors.Espresso
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جارٍ تأكيد الدفع...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تأكيد ودفع ${plan.priceLabel}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "بياناتك محمية • يمكنك إلغاء الاشتراك في أي وقت",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun PinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(350)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // The sheet may already be dismissed.
        }
    }

    BasicTextField(
        value = pin,
        onValueChange = { raw ->
            onPinChange(raw.filter { it.isDigit() }.take(4))
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { keyboardController?.hide() }
        ),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(
            color = Color.Transparent,
            fontSize = 16.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    ) { innerTextField ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { focusRequester.requestFocus() }
        ) {
            Box(modifier = Modifier.size(1.dp)) {
                innerTextField()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    val isCurrent = index == pin.length && pin.length < 4
                    val borderColor = when {
                        isError -> MaterialTheme.colorScheme.error
                        isCurrent -> SeePlusColors.DeepGold
                        filled -> SeePlusColors.DeepGold.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent) {
                                    SeePlusColors.Gold.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (filled) "•" else "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentSuccessDialog(
    plan: SubscriptionPlan,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SeePlusColors.Champagne,
                                SeePlusColors.Gold,
                                SeePlusColors.DeepGold
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = SeePlusColors.Espresso,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = "تم تفعيل seePlus",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Text(
                text = "أصبحت عضوًا في seePlus بخطة ${plan.title}. استمتع الآن بكل المزايا بدون حدود.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeePlusColors.Gold,
                    contentColor = SeePlusColors.Espresso
                )
            ) {
                Text(
                    text = "ابدأ المشاهدة",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun CancelSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "إلغاء اشتراك seePlus؟",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Text(
                text = "ستفقد مزايا seePlus فورًا، بما فيها الإطار المميز في حسابك. يمكنك الاشتراك مرة أخرى في أي وقت.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "نعم، إلغاء الاشتراك",
                    fontWeight = FontWeight.Bold,
                    color = PaletteMutedRed
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "الاحتفاظ بالاشتراك",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    )
}
